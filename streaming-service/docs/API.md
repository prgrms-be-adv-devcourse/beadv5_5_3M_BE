# API Reference — streaming-service

HTTP + WebSocket 프로토콜 명세. 클라이언트·서버 양쪽 코드를 이 문서만 보고 작성 가능한 수준.

모든 `/api/**` 와 `/ws/**` 는 gateway 경유 전제 (직접 8088 접속도 가능하지만 gateway JWT → `X-User-Id` 주입이 없어 인증 실패).

---

## 1. 인증 & 토큰

### 1.1 두 토큰 개념

| 토큰 | 발급자 | 검증자 | 용도 |
|---|---|---|---|
| **gateway JWT** | user-service 로그인 | gateway-service | `POST /api/streaming/sessions` 호출 인증 (`X-User-Id` 헤더 주입) |
| **sessionToken** | streaming-service | streaming-service | HLS `?t=...` + STOMP `token` 헤더 |

세부: `TOKEN.md`.

### 1.2 gateway 주입 헤더

- `X-User-Id`: UUID 문자열 (gateway 가 JWT `sub` 클레임에서 추출).
- streaming-service 의 `StreamingSessionController` 는 `@RequestHeader("X-User-Id") UUID userId` 로 수신.

### 1.3 sessionToken 형식

- HS256 JWT (JJWT)
- 페이로드 클레임:
  - `sub` = `sessionId` (UUID 문자열)
  - `iss` = `"streaming-service"`
  - `iat`, `exp`
- `exp` = `endTime + 10m` (Schedule 단위)

---

## 2. HTTP 엔드포인트

### 2.1 `POST /api/streaming/sessions`

세션 발급. LOBBY / ON_AIR / POST 구간에서만 허용.

**Request**

```http
POST /api/streaming/sessions
Host: localhost:8000
Authorization: Bearer <gateway-jwt>
X-User-Id: 11111111-1111-1111-1111-111111111111   # gateway 주입
Content-Type: application/json

{
  "scheduleId": 42
}
```

**Response 200**

```json
{
  "sessionToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "sessionId": "7f3c1a80-9e2f-4d81-b8d5-3c1f4a2b5e00",
  "manifestUrl": "http://localhost:8088/api/streaming/42/index.m3u8?t=eyJhbGciOi...",
  "wsEndpoint": "ws://localhost:8088/ws/stream",
  "expiresAt": "2026-04-22T21:10:00Z",
  "schedule": {
    "scheduleId": 42,
    "title": "봄밤의 라이브 상영",
    "startTime": "2026-04-22T19:00:00Z",
    "endTime": "2026-04-22T21:00:00Z",
    "imageUrl": "posters/abc/poster.jpg"
  }
}
```

**Error**

| HTTP | code | 조건 |
|---|---|---|
| 403 | `NO_ENTITLEMENT` | 해당 scheduleId 에 대한 entitlement 없음 |
| 404 | `SCHEDULE_NOT_FOUND` | schedule 미수신 |
| 410 | `WINDOW_CLOSED` | 현재 시각이 LOBBY ~ POST 밖 (CLOSED/FINISHED) |
| 502 | `STREAM_LOCATION_UNAVAILABLE` | creator-service `/internal/movies/{id}/location` 호출 실패 |

Error body:
```json
{ "code": "NO_ENTITLEMENT", "message": "해당 스케줄에 대한 입장 권한이 없습니다." }
```

**부작용**
- 같은 userId 에 활성 세션이 있으면 `/user/queue/kick` 프레임으로 기존 세션에 kick 송신 + Redis 키 교체.
- `schedule.videoPath` null 이면 creator-service HTTP 호출 후 DB 업데이트 (같은 트랜잭션).

### 2.2 `GET /api/streaming/{scheduleId}/{file}`

HLS 매니페스트·세그먼트. ON_AIR 구간에서만 허용.

**Path param**

| 이름 | 제약 |
|---|---|
| `scheduleId` | 양의 정수 |
| `file` | 정규식 `^[a-zA-Z0-9_\-]+\.(m3u8\|ts)$` |

**Query param**

| 이름 | 제약 |
|---|---|
| `t` | sessionToken (필수) |

**Request**

```http
GET /api/streaming/42/index.m3u8?t=eyJhbGciOi... HTTP/1.1
```

**Response — 매니페스트 (`*.m3u8`)**

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.apple.mpegurl
Cache-Control: no-store

#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXTINF:9.967,
http://localhost:8088/api/streaming/42/segment_000.ts?t=eyJhbGciOi...
#EXTINF:10.000,
http://localhost:8088/api/streaming/42/segment_001.ts?t=eyJhbGciOi...
#EXT-X-ENDLIST
```

서버 재작성 규칙 (`ManifestRewriter`): 원 매니페스트의 모든 세그먼트 라인을 `publicBaseUrl + "/api/streaming/" + scheduleId + "/" + fileName + "?t=" + token` 으로 치환. 주석(`#` 시작) 과 빈 줄은 그대로.

**Response — 세그먼트 (`*.ts`)**

```http
HTTP/1.1 200 OK
Content-Type: video/mp2t
Content-Length: 524288

<binary>
```

**Error**

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_FILE_NAME` | 정규식 불일치, `..` 등 path traversal 시도 |
| 401 | `SESSION_EXPIRED` | JWT exp 만료 |
| 401 | `INVALID_TOKEN` | 서명 실패 / 파싱 실패 |
| 409 | `SESSION_MISMATCH` | 토큰 sessionId ≠ Redis 활성 세션 또는 path scheduleId ≠ 토큰 meta.scheduleId |
| 410 | `WINDOW_CLOSED` | `canServeHls(now) == false` (ON_AIR 아님) |
| 404 | `SEGMENT_NOT_FOUND` | 파일 부재 |

### 2.3 `GET /actuator/health`

Spring Boot 표준 health. dev 에서 `management.endpoint.health.show-details=always`, prod 는 `never`.

---

## 3. WebSocket

### 3.1 엔드포인트

- `ws://localhost:8088/ws/stream` (dev) 또는 `ws://localhost:8000/ws/stream` (gateway 경유)
- **SockJS 비활성** — 순수 WebSocket + STOMP 1.2

### 3.2 STOMP CONNECT

sessionToken 은 URL 쿼리 파라미터가 아닌 **STOMP CONNECT 프레임의 `token` 헤더**로 전송한다. 핸드셰이크 단계에서는 `X-User-Id` (gateway 주입) 만 거치고, 실제 인증은 CONNECT 프레임 도착 후 `StompAuthChannelInterceptor.preSend(CONNECT)` 에서 수행 — 토큰이 HTTP 액세스 로그·프록시 로그에 남지 않는 장점. 브라우저 JS(`@stomp/stompjs`) 는 `connectHeaders: { token: ... }` 로 세팅.

**Frame**

```
CONNECT
accept-version:1.2
host:localhost
token:eyJhbGciOi...
heart-beat:10000,10000

\0
```

**성공 응답**

```
CONNECTED
version:1.2
heart-beat:10000,10000

\0
```

서버는 CONNECT 1회 검증 후 `simpSessionAttributes` 에 다음을 캐시 — 이후 SUBSCRIBE/SEND 에서는 DB/Redis 재조회 없음 (WebSocket 은 장시간 연결, 메시지마다 재조회는 비효율):
- `userId: UUID`
- `scheduleId: Long`
- `sessionId: UUID`
- `entitlementVerified: true`
- `ticketId: Long`
- `nickname: String` (`"관람객#" + ticketId`)

**에러 응답 (ERROR 프레임으로 close)**

| `message` 헤더 | 원인 |
|---|---|
| `INVALID_TOKEN` | 서명 실패 |
| `SESSION_EXPIRED` | JWT exp 만료 |
| `SESSION_MISMATCH` | Redis 에 세션 없음 (kick 됨) 또는 userId 불일치 |
| `SCHEDULE_NOT_FOUND` | scheduleId 로 schedule 조회 실패 |
| `WINDOW_CLOSED` | CLOSED/FINISHED |
| `NO_ENTITLEMENT` | entitlement 없음 |

### 3.3 SUBSCRIBE 토픽 (서버 → 클라이언트)

#### 3.3.1 `/topic/chat/schedule/{scheduleId}`

채팅 broadcast. schedule-scoped.

**Payload** (`ChatMessage`)
```json
{
  "messageId": "3b2e8f4a-4c6d-11ef-9c8a-00155d000001",
  "userId": "11111111-1111-1111-1111-111111111111",
  "nickname": "관람객#1001",
  "content": "hi",
  "at": "2026-04-22T19:05:23.412Z"
}
```

#### 3.3.2 `/topic/viewers/schedule/{scheduleId}`

동시 시청자 수. 5초 주기(`streaming.viewer.broadcast-interval-seconds`) + 연결/해제 즉시.

**Payload** (`ViewerCountMessage`)
```json
{ "count": 42, "at": "2026-04-22T19:05:20.000Z" }
```

#### 3.3.3 `/topic/state/schedule/{scheduleId}`

재생 상태 변경 (Quartz Job 발동 시점).

**Payload** (`StateMessage`)
```json
{ "state": "STARTED", "at": "2026-04-22T19:00:00.000Z" }
```

`state` enum (`StreamState`): `LOBBY_OPEN`, `STARTING_SOON`, `STARTED`, `ENDING_SOON`, `ENDED`, `FORCE_EXIT`.

#### 3.3.4 `/user/queue/kick` (user-destination)

본인 세션이 다른 세션에 밀려났거나 ForceExit 발동 시. 수신 직후 서버가 WebSocket close 호출.

**Payload** (`KickMessage`)
```json
{ "reason": "DUPLICATE_LOGIN", "at": "2026-04-22T19:07:00.000Z" }
```

`reason` enum (`SessionKickReason`): `DUPLICATE_LOGIN`, `FORCE_EXIT`.

#### 3.3.5 `/user/queue/errors` (user-destination)

본인 SEND 요청에 대한 에러 (rate limit / 메시지 길이 초과).

**Payload**
```json
{
  "code": "CHAT_RATE_LIMITED",
  "message": "초당 3 건 초과"
}
```

### 3.4 SEND `/app/chat/schedule/{scheduleId}`

채팅 송신.

**Request frame**
```
SEND
destination:/app/chat/schedule/42
content-type:application/json

{"content": "hi"}
\0
```

서버가 `userId`, `nickname`, `messageId`, `at` 을 주입 후 `/topic/chat/schedule/{id}` 로 broadcast. 닉네임은 익명 — `"관람객#" + ticketId` 형식으로 서버가 STOMP CONNECT 시 `Entitlement.ticketId` 에서 계산해 세션 attribute 에 캐시한다. 클라이언트가 보낸 nickname 은 신뢰하지 않는다.

**제약**
- `content.length <= 500` (`streaming.chat.max-message-length`). 초과 시 `/user/queue/errors` 로 `CHAT_MESSAGE_TOO_LONG`.
- rate limit 3 msg/sec (`streaming.chat.rate-limit-per-second`, Redis 슬라이딩 카운터 TTL 1s). 초과 시 `/user/queue/errors` 로 `CHAT_RATE_LIMITED`.
- schedule state 가 LOBBY / ON_AIR / POST 중이어야 함. POST 이후에도 채팅은 허용 (영상 없음에도 후감상 대화).

### 3.5 SUBSCRIBE 제약

- 토픽의 `{scheduleId}` 는 **CONNECT 시 session attribute 의 scheduleId 와 일치** 해야 함.
- 불일치 시 ERROR 프레임 + 연결 close (`SCHEDULE_MISMATCH`, 추가 에러 코드).

### 3.6 Heart-beat

- 권장 `10000,10000` (10초).
- Spring 기본 설정 사용.

---

## 4. 에러 카탈로그 (통합)

| Code | HTTP | WS | Retry | Meaning |
|---|---|---|---|---|
| `NO_ENTITLEMENT` | 403 | ERROR | X | 입장 권한 없음 |
| `SCHEDULE_NOT_FOUND` | 404 | ERROR | X | 스케줄 없음 |
| `WINDOW_CLOSED` | 410 | ERROR | X | 시간 밖 |
| `INVALID_TOKEN` | 401 | ERROR | X | 서명 실패 |
| `SESSION_EXPIRED` | 401 | ERROR | X | JWT exp 지남 |
| `SESSION_MISMATCH` | 409 | ERROR | X | 세션 밀려남 / 불일치 |
| `INVALID_FILE_NAME` | 400 | - | X | HLS 파일명 오류 |
| `SEGMENT_NOT_FOUND` | 404 | - | X | 세그먼트 없음 |
| `STREAM_LOCATION_UNAVAILABLE` | 502 | - | O (1회) | creator-service HTTP 실패 |
| `CHAT_RATE_LIMITED` | - | `/user/queue/errors` | X | 3 msg/sec 초과 |
| `CHAT_MESSAGE_TOO_LONG` | - | `/user/queue/errors` | X | content > 500자 |

**Retry** = 클라이언트가 일정 간격 후 재시도 권장.

Error body (HTTP):
```json
{ "code": "SESSION_EXPIRED", "message": "세션이 만료되었습니다. 다시 입장해 주세요." }
```

---

## 5. 예시 시퀀스

### 5.1 풀 플로우

```
1. 클라이언트: POST /api/users/login  → gatewayJwt
2. 클라이언트: POST /api/streaming/sessions { scheduleId }  + Bearer gatewayJwt
   ← { sessionToken, sessionId, manifestUrl, wsEndpoint, ... }
3. 클라이언트: GET manifestUrl (= /api/streaming/42/index.m3u8?t=sessionToken)
   ← 재작성된 매니페스트
4. 클라이언트 HLS 플레이어: GET segment URLs (포함된 ?t=...)
   ← 세그먼트 바이트
5. 클라이언트: ws connect + STOMP CONNECT (token=sessionToken)
   ← CONNECTED
6. 클라이언트: SUBSCRIBE /topic/chat/schedule/42
   SUBSCRIBE /topic/viewers/schedule/42
   SUBSCRIBE /topic/state/schedule/42
   SUBSCRIBE /user/queue/kick
7. 클라이언트: SEND /app/chat/schedule/42 { content: "hi" }
   ← /topic/chat/schedule/42 에 broadcast
```

### 5.2 단일 세션 kick

```
- UserA 브라우저1: 세션 A 발급 + WS 연결
- UserA 브라우저2: 세션 B 발급
  └ 서버: SessionCachePort.findByUser(UserA) → A 발견
  └ 서버: KickNotifierPort.notify(UserA, DUPLICATE_LOGIN) → /user/queue/kick broadcast
  └ 서버: Redis 키 교체
  └ 응답: { sessionToken=B, ... }
- 브라우저1: /user/queue/kick 수신 → UI "다른 기기에서 입장했습니다"
- 브라우저1: 이후 HLS 요청 → 409 SESSION_MISMATCH
```

---

## 6. 관련 문서

- `TOKEN.md` — sessionToken 상세 (HS256, 발급·검증·revocation)
- `DOMAIN-MODEL.md` — enum (`SessionKickReason`, `StreamState`) · Entitlement · Schedule
- `DESIGN.md §5` — 시청 시작 / HLS / WebSocket 플로우 다이어그램
- ADR 0003 (HS256) · 0005 (매니페스트 rewrite) · 0010 (CONNECT 1회 검증)
