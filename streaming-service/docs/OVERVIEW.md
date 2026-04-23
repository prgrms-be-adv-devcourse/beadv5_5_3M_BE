# streaming-service 전체 흐름 개요

한눈에 보는 문서. 세부 설계는 `DESIGN.md`, 토큰(sessionToken) 은 `TOKEN.md`, 운영 문제는 `TROUBLESHOOTING.md` 참고.

문서 인덱스:
- `ARCHITECTURE.md` — 포트폴리오용 원페이지 요약 (여기부터 읽어도 됨)
- `DOMAIN-MODEL.md` — 엔티티·DDL·enum
- `MESSAGE-SCHEMAS.md` — Kafka payload
- `API.md` — HTTP + WebSocket 스펙
- `CONFIG.md` — property 전체
- `RUNBOOK.md` — 구동 절차 · Smoke Test · 트러블슈팅
- `TROUBLESHOOTING.md` — 운영 문제/해결 모음
- `ONBOARDING.md` — 신규 팀원 진입 가이드
- `TOKEN.md` — sessionToken 설계
- `DESIGN.md` — 상세 설계·플로우 다이어그램
- `adr/README.md` — 핵심 결정 5건 (ADR)

---

## 1. 이 서비스가 하는 일

티켓을 산 사람만, **정해진 시간에 동기화된 라이브 상영**을 볼 수 있게 한다.
추가로 **채팅 · 동시 시청자 수 · 재생 상태 알림**을 제공한다.

- VOD 아님 → 유저가 임의 시점부터 못 본다. `startTime` 기준으로 서버 계산 오프셋부터 재생.
- 입장 가능 구간: `startTime - 10m ~ endTime + 10m`
- 영상 재생 구간: `startTime ~ endTime`
- 계정당 동시 시청: **1세션** (새 세션이 기존을 밀어냄)
- `startTime` 이후 환불 불가 (플랫폼 정책)

---

## 2. 주변 서비스와의 관계

```
creator-service        ticket-service              streaming-service
─────────────────      ──────────────              ─────────────────
영상 업로드             티켓 판매                   ✅ 이 서비스
HLS 인코딩              Quartz 상태전이
                       리뷰 권한 발행
                                                   관람 검증
                                                   HLS 서빙
                                                   채팅 · viewer count
                                                   재생 상태 push
```

**입력**
- Kafka `movie.schedule.confirmed` (from creator-service) → 스케줄 사본 적재
- Kafka `ticket.review.authorized` (from ticket-service) → 관람 권한 적재
- HTTP `GET /internal/movies/{id}/location` (to creator-service) → 영상 경로 지연 조회

**출력**
- `/api/streaming/sessions` — 세션 토큰 발급
- `/api/streaming/{scheduleId}/{file}` — HLS 매니페스트/세그먼트 서빙
- `/ws/stream` — WebSocket (채팅 / viewer count / 재생 상태)

**Gateway**
- `/api/streaming/**` → `streaming-service:8088` 라우팅
- JWT 검증 후 `X-User-Id` 헤더 주입

---

## 3. 라이프사이클

```
              startTime-10m    startTime          endTime        endTime+10m
─────────────────┬──────────────┬──────────────────┬──────────────┬─────────
     CLOSED      │    LOBBY     │     ON_AIR       │     POST     │ FINISHED
                 │              │                  │              │
   입장 불가     │  대기실 입장 │  영상 재생 +     │ 채팅만 유지  │  강제 퇴장
                 │  (채팅만)    │  채팅 + viewer   │  (영상 X)    │  세션 정리
```

| 상태 | 세션 발급 | HLS 서빙 | WebSocket |
|---|---|---|---|
| CLOSED | ❌ | ❌ | ❌ |
| LOBBY | ✅ | ❌ | ✅ 채팅만 |
| ON_AIR | ✅ | ✅ | ✅ 전체 |
| POST | ✅ (기존 세션 유지) | ❌ | ✅ 채팅만 |
| FINISHED | ❌ | ❌ | 강제 DISCONNECT |

상태 전이는 **Quartz 1회성 Job 6개**(LobbyOpen / StartingSoon / Started / EndingSoon / Ended / ForceExit)가 스케줄별로 등록돼서 담당한다.

---

## 4. 핵심 플로우 3가지

### 플로우 A. 스케줄·권한 적재 (비동기)
```
creator-service ──movie.schedule.confirmed──▶ streaming-service
                                              └─ schedule 저장 + Quartz Job 6개 등록

ticket-service  ──ticket.review.authorized──▶ streaming-service
                                              └─ entitlement 저장
                                                 (startTime-10m 이전까지 완료 필요)
```

### 플로우 B. 시청 시작
```
browser ──POST /api/streaming/sessions { scheduleId }──▶ streaming-service
                                                         ├─ entitlement 검증 (없으면 403)
                                                         ├─ 시간 검증 (LOBBY~POST만 허용)
                                                         ├─ 기존 세션 kick (Redis + WS)
                                                         ├─ 신규 sessionId 발급
                                                         └─ StreamAddressPort.resolve()
browser ◀──{ sessionToken, manifestUrl, wsEndpoint }──
```

### 플로우 C. 실시간 시청
```
browser ──GET /api/streaming/{id}/index.m3u8?t=token──▶ streaming-service
        ◀──manifest (segment URL에 token 주입)──
        ──GET segment_001.ts?t=token──▶
        ◀──바이트──

browser ──WS /ws/stream (STOMP)──▶ streaming-service
        /topic/chat/schedule/{id}       ── 채팅
        /topic/viewers/schedule/{id}    ── 동시 시청자 수 (5초 주기)
        /topic/state/schedule/{id}      ── 재생 상태 알림
        /queue/kick                     ── 본인 세션이 밀려났을 때
```

---

## 5. 데이터 저장 요약

**PostgreSQL (`streaming_db`)**
- `schedule` — 스케줄 사본 (startTime, endTime, video_path 등)
- `entitlement` — 관람 권한 (userId, scheduleId, ticketId)

**Redis**
- `stream:session:user:{userId}` — 유저당 현재 세션 (단일 세션 enforcement)
- `stream:session:id:{sessionId}` — 토큰 → 유저 매핑
- `stream:viewers:schedule:{scheduleId}` — SET of userIds (viewer count용)

TTL은 `endTime + 10m`.

---

## 6. 추상화 포인트

**StreamAddressPort** — HLS 주소 해석을 인터페이스화.
MVP는 마운트된 버킷을 직접 서빙(`LocalDirectStreamAddressAdapter`).
나중에 creator-service 위임 + 서명 URL(`SignedUrlStreamAddressAdapter`) 또는 CDN(`CdnStreamAddressAdapter`)으로 전환할 때 **어댑터 교체 + properties 한 줄 변경**으로 끝난다.

---

## 7. 왜 이렇게 설계했나 (핵심 결정 3가지)

| 결정 | 이유 |
|---|---|
| 동기화 라이브 (VOD 아님) | 라이브 상영 플랫폼 컨셉. 동시 시청자 · 채팅 · 재생 상태 같은 UX가 의미를 가짐 |
| Kafka 이벤트로 권한/스케줄 사본 적재 | 서비스 간 느슨한 결합. streaming-service가 ticket-service에 HTTP 조회하지 않음 |
| 단일 세션 + kick-old | 계정 공유 차단. Netflix/Disney+ 방식. 사용자가 로그아웃 없이도 기기 이동 가능 |

나머지 결정은 `DESIGN.md §3` 표 또는 `adr/README.md` 의 ADR 5건 (단일 노드 · HS256 · 매니페스트 rewrite · CONNECT 1회 검증 · 즉시 ForceExit) 참고.