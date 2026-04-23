# 0010. Entitlement 재검증: CONNECT 1회 + session attribute 캐시

- **Status**: Accepted
- **Date**: 2026-04-21
- **Deciders**: 소유자

## Context

WebSocket 연결은 장시간 (수십 분 ~ 수 시간) 유지된다. 그 동안 발생하는 STOMP 프레임 종류:

- **SUBSCRIBE** — 토픽 구독 (`/topic/chat/schedule/{id}` 등)
- **SEND** — 채팅 메시지 송신 (`/app/chat/schedule/{id}`)

각 프레임마다 "이 유저가 이 스케줄에 대한 entitlement 가 있는가?" 를 확인할지가 문제.

옵션:
1. **매 SUBSCRIBE/SEND 마다 DB 조회** — 정확하지만 프레임마다 DB round-trip. 장시간 연결 × 초당 여러 메시지 시 DB 부하.
2. **TTL 캐시 (Redis 또는 로컬)** — 수 분 TTL 로 가용. 복잡도 증가.
3. **CONNECT 1회 검증 + session attribute 캐시** — 연결 수립 시점에 DB 한 번 조회, `simpSessionAttributes` 에 결과 캐시. 이후 프레임은 attribute 만 확인.

`Entitlement` 는 환불·관리자 취소 외에는 변경되지 않고, `startTime` 이후 환불 불가 정책이 있으므로 장시간 연결 중 entitlement 가 사라질 확률은 낮다. ForceExit 은 별도 `/user/queue/kick` 경로로 처리되므로 entitlement 재조회와 무관.

## Decision

CONNECT 시점에 **1회** entitlement 조회 후 `simpSessionAttributes` 에 캐시한다. 이후 SUBSCRIBE/SEND 에서는 attribute 만 확인.

Session attributes 구성:
- `userId: UUID` — sessionToken 파싱 + `SessionCachePort.findBySession` 결과
- `scheduleId: Long` — `ActiveSession.scheduleId`
- `sessionId: UUID` — 토큰 sub
- `entitlementVerified: Boolean` (true 면 통과, false 면 CONNECT 자체가 ERROR 로 끊김)

검증 흐름 (`StompAuthChannelInterceptor.preSend`):

```
if command == CONNECT:
  token = headers.token
  sessionId = StreamTokenPort.parse(token)   // 만료/위조 → ERROR INVALID_TOKEN/SESSION_EXPIRED
  meta = SessionCachePort.findBySession(sessionId)
      .orElseThrow(SESSION_MISMATCH)
  active = SessionCachePort.findByUser(meta.userId)
      .filter(a -> a.sessionId.equals(sessionId))
      .orElseThrow(SESSION_MISMATCH)          // 이미 kick 됐으면 Redis 에 없음
  schedule = ScheduleRepository.findById(meta.scheduleId)
      .orElseThrow(SCHEDULE_NOT_FOUND)
  if !schedule.canEnterSession(now): throw WINDOW_CLOSED
  if !EntitlementRepository.exists(meta.userId, meta.scheduleId): throw NO_ENTITLEMENT
  simpSessionAttributes.putAll(userId, scheduleId, sessionId, entitlementVerified=true)

if command == SUBSCRIBE or SEND:
  assert simpSessionAttributes.entitlementVerified == true
  // scheduleId mismatch 검증 (구독하는 topic 의 scheduleId 가 attribute 와 일치하는지)
```

## Consequences

- **Positive**:
  - DB 부하 최소 — 연결당 1회 조회.
  - Spring 표준 훅 (`ChannelInterceptor` + `simpSessionAttributes`) 사용. 코드 단순.
  - 연결 수립 단계에서 권한 없는 유저를 즉시 차단 — 에너지 낭비 없음.

- **Negative**:
  - 연결 중에 entitlement 가 취소되어도 재검증 안 됨 — 단, 환불이 `startTime` 이후 불가 정책이라 현실에서는 드묾.
  - ForceExit(스케줄 종료 후) 시 재검증을 거치지 않지만, 이는 별도 kick 경로로 처리되므로 문제 없음 (ADR 0012).

- **Neutral**:
  - 추후 "연결 중 관리자 취소" 시나리오가 요구되면, 관리자 API 에서 `KickNotifierPort.notify(userId, ADMIN_ACTION)` 을 호출해 강제 해제하는 방식으로 확장 가능.

## Alternatives Considered

- **매 SUBSCRIBE/SEND 재조회**: 정확하지만 DB 부하 큼. 기각: 현실적 변경률 대비 과도.
- **TTL 캐시 (Redis 1min)**: 중간 안. 기각: CONNECT 1회 캐시가 더 단순하고, 변경률이 낮아 TTL 이점 미미.

## Related

- ADR 0012 — ForceExit (kick 경로)
- `infrastructure/websocket/StompAuthChannelInterceptor.java` — 실제 구현
- `API.md §3` — WS CONNECT 스펙
