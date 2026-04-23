# 0012. ForceExit = 즉시 kick + WS close + Redis 삭제

- **Status**: Accepted
- **Date**: 2026-04-21
- **Deciders**: 소유자

## Context

`endTime + 10m` 에 도달하면 (`ForceExitJob`), 해당 스케줄의 모든 연결을 정리해야 한다. 옵션:

1. **Graceful (15s 유예)**: 상태 브로드캐스트 → 15s 카운트다운 → 강제 해제. 클라이언트가 안내 UI 띄울 시간 확보.
2. **TTL 자연 만료**: Redis 키 TTL 을 `endTime+10m` 으로 세팅하고 클라이언트 알림 없이 자연 종료 대기.
3. **즉시 kick**: Job 발동 즉시 broadcast 로 `state=FORCE_EXIT` 송신 + WS close + Redis 키 삭제 + viewer set purge.

트레이드오프:
- 라이브 상영은 `endTime + 10m` 이 명시된 정책이라 사용자도 예상 가능.
- Graceful 은 코드 복잡도 증가 (추가 타이머 Job) 대비 UX 이득 크지 않음.
- TTL 만료는 "서버가 아무 것도 안 하는" 상태라 실패 감지가 어려움.

## Decision

ForceExit 은 **즉시** 실행한다:

1. `/topic/state/schedule/{id}` 에 `StreamState.FORCE_EXIT` broadcast
2. 해당 스케줄의 모든 유저 세션에 대해:
   - `/user/queue/kick` 에 `{reason: FORCE_EXIT}` 송신
   - WebSocket 세션 강제 종료 (`SimpUserRegistry` 에서 해당 유저 찾아 `WebSocketSession.close()`)
3. Redis 키 삭제:
   - `stream:viewers:schedule:{scheduleId}` purge
   - 해당 스케줄에 연결된 모든 `stream:session:user:{userId}` 및 `stream:session:id:{sessionId}` 삭제 (SCAN + DEL 배치)

클라이언트 UX 는 `FORCE_EXIT` 상태 수신 시 "상영이 종료되었습니다" 다이얼로그로 처리.

## Consequences

- **Positive**:
  - 구현 단순 — 추가 타이머 Job 불요.
  - 상태 일관성 강함 — Job 실행 직후 Redis·WS·viewer set 모두 0.
  - 테스트 수월 — 부수 효과가 순차적·즉시 실행.

- **Negative**:
  - 클라이언트 UI 가 "갑자기 연결 끊김" 으로 인식될 수 있음 → `FORCE_EXIT` state 메시지가 WS close 전에 도착해야 UX 자연스러움. 서버에서 broadcast → close 순서 보장.
  - Redis `SCAN` 비용 (수천 명 이하 가정 하에 문제 없음, ADR 0002).

- **Neutral**:
  - 추후 UX 요구 생기면 "15s 유예 + 최종 kick" 로 확장 가능 — Quartz Job 2개 (`EndingSoon` 이 이미 존재) 조정.

## Alternatives Considered

- **Graceful (15s 유예)**: UX 부드러움. 기각: 추가 복잡도 대비 이득 작음.
- **TTL 자연 만료**: Redis 의 keyspace notification 에 의존. 기각: 실패 감지·재시도 경로 없음, 서버가 명시적 브로드캐스트를 못 함.

## Related

- ADR 0002 — 단일 노드 (SCAN 비용 가정)
- ADR 0010 — Entitlement CONNECT 캐시 (kick 경로 분리 이유)
- `DESIGN.md §5.6` — 상태 전이 Quartz Job 표
- `infrastructure/scheduler/ForceExitJob.java` — 실제 구현
- `DOMAIN-MODEL.md §4` — `SessionKickReason.FORCE_EXIT` enum
