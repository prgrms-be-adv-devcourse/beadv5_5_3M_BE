# streaming-service 설계 문서

## 1. 개요

`streaming-service`는 MSA 상의 다섯 번째 Spring Boot 서비스로서, 티켓을 보유한 사용자가 **지정된 상영 시간 동안** HLS 스트림을 안전하게 시청하고 동시에 라이브 UX(채팅 · 시청자 수 · 재생 상태)를 경험할 수 있도록 하는 엔드 서비스다.

주변 서비스들이 스트리밍 라이프사이클의 앞뒤를 이미 구현한 상태이고, 본 서비스는 "실제 방송 중" 구간의 책임만 채운다.

| 경계 | 책임 |
|---|---|
| creator-service | 영상 업로드 · HLS 인코딩 · 영상 메타데이터 |
| ticket-service | 티켓팅 상태기계 · 결제 · 리뷰 권한 발행 |
| user-service | 인증 · 쿠키 지갑 |
| gateway-service | JWT 검증 · `X-User-Id` 헤더 주입 |
| **streaming-service (본 서비스)** | **관람자 자격 확인 · HLS 서빙 · 라이브 UX · 단일 세션 enforcement** |

---

## 2. 요구사항 · 정책

### 2.1 기능 요구사항

- 유효 티켓 보유자만 스트림 접근 가능 (권한 확인).
- **세션 창** `[startTime − 10m, endTime + 10m)` 내에서만 세션 생성·WebSocket 연결 허용.
- **재생 창** `[startTime, endTime]` 내에서만 HLS 매니페스트·세그먼트 반환.
  - 재생 창 밖이지만 세션 창 안인 구간은 **대기실 / 마무리** UX (채팅·시청자 수·상태 신호만 동작).
- 중간 입장 시 현재 오프셋(`now − startTime`)부터 재생되어야 한다 (동기화 라이브, VOD 아님).
- 라이브 채팅 제공 (스케줄별 채팅방).
- 동시 시청자 수를 모든 시청자에게 실시간 브로드캐스트.
- 재생 상태 전이 신호(`LOBBY_OPEN` · `STARTING_SOON` · `STARTED` · `ENDING_SOON` · `ENDED` · `FORCE_EXIT`)를 클라이언트에 푸시.
- 유저당 동시 시청 1세션으로 제한 (계정 공유 차단).
- `endTime + 10m` 도달 시 모든 세션·WebSocket 강제 종료.

### 2.2 비기능 요구사항 · 정책

- **환불 정책**: `startTime − 10m` 이후 환불 불가. → streaming-service는 환불 이벤트를 구독하지 않는다 (Entitlement 사본이 `startTime − 10m` 시점에 동결).
- **입장 개방 = 환불 차단 = Entitlement 사본 적재 완료** 시점을 모두 `startTime − 10m`으로 정렬. ticket-service의 `ReviewAuthQuartzJob` 발동 시각이 `startTime − 10m`으로 맞춰져 있다.
- **Java 21, Spring Boot 4.0.5, Jakarta 네임스페이스** 준수.
- **Hexagonal ports-and-adapters** 레이어링 (ticket-service 스타일).
- **Entity-owned state transition** (서비스 레이어에서 엔티티 필드 직접 수정 금지).
- Gateway가 JWT를 검증하므로 내부 인증 체인 재구성 불필요.
- **채팅 휘발성 정책**: 채팅 메시지는 DB·Redis 어디에도 저장하지 않는다. broadcast 만 담당 — 재접속 시 backlog 없음.
- **채팅 rate limit**: 유저당 3 msg/sec 고정. Redis 슬라이딩 카운터 (`RedisChatRateLimitAdapter`, TTL 1s).
- **채팅 익명 닉네임**: 닉네임은 서버 계산 `"관람객#" + ticketId`. CONNECT 시 1회 계산 후 `simpSessionAttributes`에 캐시. 실명·user-service 조회 없음.

### 2.3 타임라인 · 상태 표

스케줄 관점의 시간 구간과 각 구간에서 허용되는 동작:

```
 현재시각
   │
   ▼
───┼───────────────────┼─────────────────┼─────────────────┼──────►
   │   CLOSED          │   LOBBY         │   ON_AIR        │   POST      FINISHED
   │                   │                 │                 │
   │            T-10m          T(=start)        T(=end)            T+10m
```

| 상태 | 시간 범위 | 세션 생성 | WebSocket | HLS 매니페스트/세그먼트 | 비고 |
|---|---|:-:|:-:|:-:|---|
| CLOSED | `t < start − 10m` | ✖ | ✖ | ✖ | 404/410 |
| LOBBY | `start − 10m ≤ t < start` | ✔ | ✔ | ✖ | 대기실. 재생 요청 시 410. Entitlement 사본 완비 |
| ON_AIR | `start ≤ t ≤ end` | ✔ | ✔ | ✔ | 메인 상영 |
| POST | `end < t < end + 10m` | ✔ | ✔ | ✖ | 마무리 UX (채팅·리뷰 유도). 재생 요청 시 410 |
| FINISHED | `t ≥ end + 10m` | ✖ | ✖ (강제 종료) | ✖ | 모든 세션 kick, 토큰 폐기 |

### 2.4 MSA 연동 전제

- **운영 환경 현황**: 외부 오브젝트 스토리지(버킷)가 파일시스템으로 마운트되어 있어 creator-service·streaming-service 양쪽에서 `storage.s3-path`(dev: `C:/Users/qorwh/s3`)를 일반 파일 I/O로 접근 가능. 이 전제가 streaming-service 직접 서빙(D3)의 기반이다.
- **미래 전환 대비**: 마운트 기반이 아닌 서명 URL / CDN 모델로 갈 여지가 있으므로, 클라이언트에 돌려줄 스트림 주소는 `StreamAddressPort`로 추상화해 어댑터 교체만으로 전환되도록 설계(D9 · §4.6).
- Kafka 공통 브로커 — `application-dev.yaml` 기본값 또는 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 환경변수로 오버라이드. prod 는 `KAFKA_BOOTSTRAP_SERVERS` 필수.
- Redis는 단일 인스턴스 공유 (`localhost:6379`).
- PostgreSQL에 service 전용 DB(`streaming_db`).

---

## 3. 설계 결정

| # | 결정 | 선택지 · 근거 |
|---|---|---|
| D1 | 시청 모델 | **동기화 라이브**. `[startTime, endTime]` 외부 차단, 중간 입장은 서버 계산 오프셋부터. ticket-service의 Quartz 타이밍과 자연스럽게 맞물림. |
| D2 | 관람 권한 확인 | **Kafka 이벤트 사본**. `movie.schedule.confirmed` + `ticket.review.authorized`를 구독해 `streaming_db`에 사본을 유지하고, 시청 요청 시 로컬 DB만 조회. ticket-service와 비동기 독립. 환불 처리 불필요(정책상). |
| D3 | HLS 파일 서빙 | **streaming-service 직접**. 공유 디스크 `C:/Users/qorwh/s3` 에서 `.m3u8`/`.ts`를 직접 읽어 서빙. 권한 검증과 바이트 서빙이 한 곳에. |
| D4 | WebSocket | **채팅 + 동시 시청자 수 + 재생 상태 신호** 세 가지 모두. |
| D5 | dev/prod 통일 | creator-service의 `LocalVideoProcessingService`(dev passthrough)를 HLS 구현으로 **교체**해서 dev도 prod와 동일하게 HLS 산출. streaming-service는 HLS 경로만 알면 됨. |
| D6 | 헥사고날 네이밍 | **ticket-service 스타일**. `*Port`(출력 포트) + `*RepositoryImpl`/`*JpaRepository` 3단. Redis · Kafka · Quartz · WS 어댑터가 많아 port 명시 컨벤션이 더 정교함. |
| D7 | 단일 세션 enforcement | **전역 1세션(아이디 기준) · 새 세션 세움(kick old)**. Netflix/Disney+ 방식. 다른 기기로 이동 UX 자연스러움. |
| D8 | 타임라인 | **세션 창 `[start − 10m, end + 10m)` · 재생 창 `[start, end]`**. 대기실·마무리 UX 별도 상태. `end + 10m` 에 강제 퇴장. Entitlement 사본은 `start − 10m` 시점에 완비. |
| D9 | 스트림 주소 서빙 추상화 | **`StreamAddressPort` 출력 포트로 분리**. 현재: 마운트된 버킷 직접 서빙(`LocalDirectStreamAddressAdapter`). 미래: creator-service 위임 + 서명 URL(`SignedUrlStreamAddressAdapter`) 또는 CDN 어댑터로 무중단 교체 가능. §4.6 참조. |

---

## 4. 아키텍처

### 4.1 레이어 구조 (실제 구현)

```
com.example.streamingservice
├── domain
│   ├── Schedule, Entitlement                          (엔티티 + 도메인 메서드)
│   ├── ScheduleRepository, EntitlementRepository     (도메인 포트 인터페이스)
│   └── ScheduleStatus, StreamState, SessionKickReason (enum)
├── application
│   ├── port/           StreamTokenPort, SessionCachePort, ViewerCachePort,
│   │                   ChatRateLimitPort, StreamAddressPort, MovieLocationPort,
│   │                   StateBroadcastPort, KickNotifierPort, SchedulerPort
│   ├── usecase/        EnterStreamUseCase, HlsServingUseCase, ChatUseCase,
│   │                   LifecycleUseCase, ViewerCountUseCase
│   ├── service/        EnterStreamService, HlsServingService, ChatService,
│   │                   LifecycleService, ViewerCountService
│   ├── dto/            IssueSessionCommand, SessionIssueResult, ServeHlsQuery,
│   │                   HlsServeResult, SendChatCommand, ChatMessage,
│   │                   StateMessage, ViewerCountMessage, KickMessage,
│   │                   ActiveSession, SessionMeta, MovieLocation,
│   │                   ScheduleSummary, HlsResource
│   ├── exception/      ErrorCode enum + {Schedule,Session,StreamToken,Chat}Exception
│   └── constants/      RedisKeys, WsDestinations
├── infrastructure
│   ├── persistence/    {Schedule,Entitlement}JpaRepository +
│   │                   {Schedule,Entitlement}RepositoryImpl
│   ├── cache/          RedisSessionCacheAdapter, RedisViewerCacheAdapter,
│   │                   RedisChatRateLimitAdapter
│   ├── token/          JjwtStreamTokenAdapter                (HS256)
│   ├── address/        LocalDirectStreamAddressAdapter
│   ├── http/           CreatorMovieLocationAdapter + CreatorClientConfig
│   ├── messaging/      ScheduleConfirmedListener, TicketReviewAuthorizedListener
│   │                   (+ dto/ 하위 Payload 레코드 2종)
│   ├── scheduler/      QuartzSchedulerAdapter + Job 6종
│   │                   (LobbyOpen, StartingSoon, Started, EndingSoon, Ended, ForceExit)
│   ├── websocket/      WebSocketStateBroadcastAdapter, WebSocketKickNotifierAdapter,
│   │                   StompAuthChannelInterceptor, ChatStompController,
│   │                   SessionPresenceListener, StreamingPrincipal,
│   │                   WsSessionRegistry, WsSessionStore, ManifestRewriter
│   └── util/           KafkaMessageUtil
├── presentation
│   ├── StreamingSessionController        (POST /api/streaming/sessions)
│   ├── HlsController                     (GET /api/streaming/{id}/{file})
│   ├── JobTestController                 (dev 전용 수동 트리거)
│   ├── GlobalExceptionHandler + ErrorResponse
│   └── dto/ SessionIssueRequest
└── config/             WebSocketConfig, KafkaConfig, QuartzConfig
```

### 4.2 데이터 모델 (`streaming_db`, `ddl-auto: create` in dev)

```sql
CREATE TABLE schedule (
  schedule_id   BIGINT       PRIMARY KEY,
  movie_id      BIGINT       NOT NULL,
  creator_id    UUID         NOT NULL,
  title         VARCHAR(100) NOT NULL,
  image_url     VARCHAR(500),
  start_time    TIMESTAMP    NOT NULL,
  end_time      TIMESTAMP    NOT NULL,
  video_path    VARCHAR(500),            -- index.m3u8 상대 경로, 지연 조회
  running_time  INTEGER                 -- 초 단위
);

CREATE TABLE entitlement (
  id          BIGSERIAL   PRIMARY KEY,
  user_id     UUID        NOT NULL,
  schedule_id BIGINT      NOT NULL REFERENCES schedule(schedule_id),
  ticket_id   BIGINT      NOT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, schedule_id)
);
CREATE INDEX idx_entitlement_schedule ON entitlement (schedule_id);
```

엔티티 메서드:
- `Schedule.attachVideoLocation(String videoPath, Integer runningTime)` — startTime 직전 지연 조회 결과 반영
- `Schedule.canEnterSession(LocalDateTime now)` — `startTime − 10m ≤ now < endTime + 10m` 판정. 세션·WebSocket 진입 게이트
- `Schedule.canServeHls(LocalDateTime now)` — `startTime ≤ now ≤ endTime` 판정. HLS 매니페스트·세그먼트 반환 게이트
- `Schedule.isForceExitTime(LocalDateTime now)` — `now ≥ endTime + 10m` 판정. 강제 퇴장 트리거
- `Schedule.currentStatus(LocalDateTime now)` — `CLOSED | LOBBY | ON_AIR | POST | FINISHED` 계산 (§2.3 표)

### 4.3 Redis 키 (`application/constants/RedisKeys.java`)

```
stream:session:user:{userId}          HASH   { sessionId, scheduleId, wsSessionId, issuedAt }
                                      TTL    endTime + 10m

stream:session:id:{sessionId}         HASH   { userId, scheduleId }
                                      TTL    endTime + 10m

stream:viewers:schedule:{scheduleId}  SET    <userId...>
                                      TTL    endTime + 10m
```

#### 4.3.1 `stream:session:user:{userId}` — 유저 기준 현재 세션

**역할**: "이 유저가 지금 보고 있는 세션 한 건"의 메타데이터. 키 이름이 `user:`로 시작하므로 **유저 기준 인덱스**. D7(단일 세션 enforcement)의 근거 저장소.

**각 필드의 쓰임**:
| 필드 | 쓰임 |
|---|---|
| `sessionId` | 새 세션 발급 시 이 값으로 `stream:session:id:{oldSessionId}`를 `DEL` → 옛 토큰 즉시 무효화 |
| `scheduleId` | 로그/감사용. 어떤 스케줄을 보던 세션이었는지 |
| `wsSessionId` | `SimpMessagingTemplate`로 `/queue/kick` 보낼 때의 target. STOMP 연결마다 바뀌므로 WS 재연결 시 이 필드만 `HSET`으로 갱신 (플로우 E (4)) |
| `issuedAt` | 디버깅·로그. 세션 발급 시각 |

**왜 HASH인가 (STRING/JSON 아님)**: `wsSessionId`만 갱신하는 경로(WS 재연결)가 있어 필드 단위 부분 갱신이 자연스러움. STRING이면 읽고-파싱-수정-직렬화-쓰기 4단계가 필요. HASH는 `HSET key field value` 한 방.

**사용 시점**: 플로우 C (4) 기존 세션 조회 / C (6) 신규 저장 / E (4) `wsSessionId` 갱신 / F(ForceExitJob) 삭제.

#### 4.3.2 `stream:session:id:{sessionId}` — 세션 기준 역방향 인덱스

**역할**: `sessionId → userId/scheduleId` 역참조. sessionToken(JWT)에는 `sub=sessionId`만 담기므로, 들어온 토큰이 "살아있는 세션"인지 확인하려면 sessionId 기준으로 조회할 수 있어야 한다.

**왜 §4.3.1과 별도로 두는가 — 양방향 인덱스**:

| 상황 | 아는 값 | 찾는 값 | 쓰는 키 |
|---|---|---|---|
| 새 세션 발급 · kick 대상 찾기 | userId (헤더에서) | 기존 sessionId | `session:user:{userId}` |
| HLS 요청 · WS CONNECT 검증 | sessionId (토큰에서) | userId + scheduleId | `session:id:{sessionId}` |

Redis엔 JOIN이 없으니 양방향 인덱스를 각각 둔다. 스토리지 약간 쓰고 조회 1-hop 보장.

**세션 revocation 역할**: JWT 자체는 stateless라 서버 강제 종료가 안 되지만, **이 키를 `DEL`하면 해당 토큰은 죽는다**. 플로우 D (2)·플로우 E (2)의 첫 관문이 "이 키 존재?"이기 때문. 즉 `stream:session:id:*`가 Redis 기반 revocation list.

**사용 시점**: 플로우 C (4) 옛 세션 `DEL` / C (6) 신규 저장 / D (2) HLS 토큰 검증 / E (2) WS CONNECT 검증 / F 삭제.

#### 4.3.3 `stream:viewers:schedule:{scheduleId}` — 스케줄별 시청자 집합

**역할**: 해당 스케줄에 라이브로 붙어있는 유저 집합. 시청자 수 브로드캐스트(플로우 G)의 소스.

**왜 SET인가 (INCR/DECR 카운터 아님) — 재연결 멱등성**:
- 네트워크 끊김으로 재연결하면 DISCONNECT + CONNECT가 연달아 발생. 카운터 기반이면 순서 꼬임/중복으로 숫자가 틀어질 수 있음 (최악의 경우 음수).
- SET은 같은 userId를 여러 번 `SADD` 해도 count=1, 없는데 `SREM` 해도 무시. **멱등**.
- 부가 효과: "누가 보고 있는지"를 알 수 있어, 추후 관전 모드·어뷰즈 감지에 그대로 재사용 가능. 카운터는 숫자만 남아 복구 불가.

**시청자 수 계산**: `SCARD key` — O(1). 플로우 G가 5초 주기로 active 스케줄별 1콜씩만 발생.

**사용 시점**: 플로우 E 첫 CONNECT `SADD` / E DISCONNECT `SREM` / G `SCARD` 후 푸시 / F 삭제.

#### 4.3.4 세 키 공통 설계 원칙

- **TTL 통일 = `endTime + 10m`**: `ForceExitJob`이 명시적으로 지우지만, Job 누락·서비스 재시작·스케줄러 오동작 시에도 Redis TTL이 **안전망**으로 자동 정리. 키마다 다른 TTL을 두면 "세션은 살아있는데 viewer 집합에서는 빠진" 식의 불일치가 생길 수 있음.
- **DB가 아니라 Redis에 두는 이유**: 전부 "지금 이 순간" 상태. 영속성 불필요하고 초당 갱신·조회가 많음. DB 트랜잭션·인덱스 비용을 쓸 이유가 없고, 서비스가 재시작되면 어차피 재구축해야 하는 데이터.
- `stream:session:*`는 플로우 C의 compare-and-swap, 플로우 D·E의 세션 검증, 플로우 F의 일괄 정리에 모두 재사용.

### 4.4 WebSocket 토픽 (`application/constants/WsDestinations.java`)

| 경로 | 방향 | 페이로드 |
|---|---|---|
| `/topic/chat/schedule/{id}` | 서버 → 구독자 | `ChatMessage { messageId, nickname, text, sentAt }` — nickname 은 `"관람객#" + ticketId` (서버 계산) |
| `/app/chat/schedule/{id}` | 클라 → 서버 | `ChatSendRequest { text }` (발신자는 세션 attribute 에서 추출) |
| `/topic/viewers/schedule/{id}` | 서버 → 구독자 | `ViewerCountMessage { scheduleId, count }` |
| `/topic/state/schedule/{id}` | 서버 → 구독자 | `StateMessage { scheduleId, state }` — state ∈ `StreamState` |
| `/user/{userId}/queue/kick` | 서버 → 특정 유저 | `KickMessage { reason }` — reason ∈ `SessionKickReason` (DUPLICATE_LOGIN · FORCE_EXIT) |
| `/user/{userId}/queue/errors` | 서버 → 특정 유저 | 에러 프레임 (CHAT_RATE_LIMITED 등) |

### 4.5 출력 포트 ↔ 어댑터

| Port | Adapter | 비고 |
|---|---|---|
| `StreamTokenPort` | `JjwtStreamTokenAdapter` | HS256 sessionToken 발급·파싱 (TOKEN.md) |
| `SessionCachePort` | `RedisSessionCacheAdapter` | `stream:session:user:*`, `stream:session:id:*` |
| `ViewerCachePort` | `RedisViewerCacheAdapter` | `stream:viewers:schedule:*` (SET) |
| `ChatRateLimitPort` | `RedisChatRateLimitAdapter` | `stream:ratelimit:chat:*` (INCR+EXPIRE, 3/sec) |
| `StreamAddressPort` | `LocalDirectStreamAddressAdapter` (현재) / `SignedUrlStreamAddressAdapter` (미래) | 매니페스트 URL 리졸버. §4.6 참조 |
| `MovieLocationPort` | `CreatorMovieLocationAdapter` | `RestClient` → creator-service `/internal/movies/{id}/location`. `videoPath` 지연 조회 |
| `StateBroadcastPort` | `WebSocketStateBroadcastAdapter` | `/topic/state/schedule/{id}` 푸시 |
| `KickNotifierPort` | `WebSocketKickNotifierAdapter` | `/user/queue/kick` 푸시 + WS 강제 종료 |
| `SchedulerPort` | `QuartzSchedulerAdapter` | Quartz 6개 Job 등록·취소 |

입력 포트(UseCase)는 `application/usecase/`, 도메인 포트는 `domain/`에 정의.

### 4.6 `StreamAddressPort` — 서빙 방식 추상화

스트림 바이트를 브라우저에 전달하는 **위치**는 향후 운영 환경 변화에 따라 바뀔 수 있다 (현재: 마운트된 버킷을 streaming-service 가 직접 읽어 서빙 / 미래: creator-service `/files/**` 위임 + 서명 URL, CDN 앞단 배치 등). `EnterStreamService`가 하드코딩된 경로를 반환하면 이 전환이 streaming-service 의 로직 변경으로 이어지므로, **세션 응답에 실릴 `manifestUrl` 생성 지점**을 출력 포트로 분리한다.

```java
// application/port/out/StreamAddressPort.java
public interface StreamAddressPort {
    StreamAddress resolve(Schedule schedule, StreamSession session);
}

public record StreamAddress(
    String manifestUrl,   // 브라우저가 직접 호출할 URL (절대 또는 gateway 상대)
    Duration ttl          // 이 URL 유효기간 (세션 토큰 TTL 또는 서명 만료)
) {}
```

**어댑터 교체 매트릭스**

| 어댑터 | 반환 URL 예시 | `HlsFileController` 활성? | 적합한 상황 |
|---|---|:-:|---|
| `LocalDirectStreamAddressAdapter` (MVP) | `/api/streaming/{id}/index.m3u8?t={sessionToken}` | ✔ | 마운트된 버킷을 직접 서빙. 현재 운영 환경 |
| `SignedUrlStreamAddressAdapter` (미래) | `http://creator-service/files/movies/.../index.m3u8?sig=XYZ&exp=...` | ✖ | creator-service 위임 + 서명 URL. streaming-service 부하/비용 감소 |
| `CdnStreamAddressAdapter` (잠재) | `https://cdn.example.com/streams/{id}/index.m3u8?token=...` | ✖ | CDN edge에서 서빙 |

**전환 절차** (미래 시점):
1. 새 어댑터 구현 (예: `SignedUrlStreamAddressAdapter` — creator-service에 서명 URL 발급 `/internal/...` 추가)
2. Spring `@Profile` 또는 `@ConditionalOnProperty(name = "streaming.address.adapter", havingValue = "signed-url")`로 Bean 스왑
3. `HlsFileController`·`LocalHlsFileAdapter` 는 그대로 두되 프로퍼티로 비활성 (또는 fallback)
4. streaming-service 재배포 → 클라이언트는 응답의 `manifestUrl`만 보므로 변경 무감지

**불변 조건** (어댑터와 무관):
- 권한 검증(`entitlement` + `canServeHls`)은 어디서 하든 streaming-service 가 먼저 수행.
- 서명 URL 어댑터의 경우, streaming-service 가 권한을 통과시킨 세션에 한해서만 발급 요청 → 서명 URL 자체가 검증의 결과물.

---

## 5. 주요 플로우

### 5.1 플로우 A. 스케줄 사본 적재 (`movie.schedule.confirmed` 수신)

```
creator-service              Kafka                streaming-service
─────────────────            ─────                ──────────────────
confirm schedule ──► movie.schedule.confirmed ──► ScheduleConfirmedListener
                                                     │ (UPSERT Schedule, videoPath = null)
                                                     ▼
                                                 SchedulerPort.registerLifecycleJobs()
                                                   LobbyOpenJob     at startTime − 10m
                                                   StartingSoonJob  at startTime − 1m
                                                   StartedJob       at startTime
                                                   EndingSoonJob    at endTime − 1m
                                                   EndedJob         at endTime
                                                   ForceExitJob     at endTime + 10m
```

### 5.2 플로우 B. 관람 권한 사본 적재 (`ticket.review.authorized` 수신)

```
ticket-service               Kafka                 streaming-service
─────────────────            ─────                 ──────────────────
ReviewAuthQuartzJob
  at startTime − 10m, CONFIRMED 티켓 N건을 per-ticket 발행
  (환불 차단 시점과 동일. §8.1 참조)
                         ─► ticket.review.authorized ─► TicketReviewAuthorizedListener
                                                           │
                                                           ▼
                                                        Entitlement UPSERT
                                                          (user_id, schedule_id, ticket_id)
                                                          unique (user_id, schedule_id)
```

타이밍 상 `LobbyOpenJob`과 ticket-service 의 `ReviewAuthQuartzJob`이 같은 `startTime − 10m`에 발동되므로, 대기실이 열리는 순간 관람자 집합이 완성된다. 이벤트 지연 대비해 LobbyOpenJob WS 푸시는 AFTER_COMMIT 기반이며, CONNECT 시 Entitlement 조회가 실패하면 `NO_ENTITLEMENT` 로 재시도 유도.

### 5.3 플로우 C. 시청 시작 (`POST /api/streaming/sessions`)

```
Browser                              streaming-service
───────                              ──────────────────
POST /api/streaming/sessions
Body: { scheduleId }
X-User-Id: {userId}  ◄── Gateway 주입

                              StreamingSessionController
                                 ▼
                              EnterStreamService.issue(IssueSessionCommand):
                                (1) ScheduleRepository.findById(scheduleId)
                                    └─ 없으면 → SCHEDULE_NOT_FOUND (404)
                                (2) schedule.canEnterSession(now) 검사
                                    └─ false → WINDOW_CLOSED (410)
                                (3) EntitlementRepository.find(userId, scheduleId)
                                    └─ 없으면 → NO_ENTITLEMENT (403)
                                (4) SessionCachePort.findByUser(userId) 로 기존 세션 확인
                                      └─ 존재 시
                                           KickNotifierPort.notify(userId, DUPLICATE_LOGIN)
                                           SessionCachePort.evict(...)
                                (5) schedule.videoPath null 이면
                                      MovieLocationPort.fetch(movieId)
                                      └─ schedule.attachVideoLocation(videoUrl, runningTime)
                                (6) sessionId = UUID, sessionToken = JWT(sub=sessionId, exp=endTime+10m)
                                     (발급·서명·파싱 상세는 TOKEN.md)
                                (7) SessionCachePort.put(userId, ActiveSession, ttl)
                                     TTL = endTime + 10m − now
                                (8) StreamAddressPort.resolveManifestUrl(scheduleId, videoPath)
                                     + ?t={token} append

                              Response 200 (SessionIssueResult):
                                { token,
                                  sessionId,
                                  manifestUrl,      // StreamAddressPort + 토큰 주입
                                  wsEndpoint: "/ws/stream",
                                  expiresAt,
                                  schedule: { scheduleId, title, startTime, endTime, imageUrl } }
```

클라이언트는 `manifestUrl` 을 `<video>` 에 물리고, `/ws/stream` 에 STOMP CONNECT (헤더 `token`) 로 채팅·뷰어·상태 토픽을 구독한다. HLS 는 `canServeHls(now)` 즉 `[startTime, endTime]` 구간에서만 200 응답 — 대기실/마무리 요청은 플로우 D 에서 410.
### 5.4 플로우 D. HLS 서빙 (`GET /api/streaming/{scheduleId}/{file}?t=...`)

```
Browser                              streaming-service
───────                              ──────────────────
GET /api/streaming/{id}/index.m3u8?t={sessionToken}

                              HlsController
                                 ▼
                              HlsServingService.serve(ServeHlsQuery):
                                (1) StreamTokenPort.parse(token) → sessionId
                                (2) SessionCachePort.findBySession(sessionId)
                                    └─ 없음 → SESSION_EXPIRED (401)
                                (3) meta.scheduleId == request scheduleId 확인
                                    └─ 불일치 → SESSION_MISMATCH (409)
                                (4) schedule.canServeHls(now) 재확인 — 재생 창 = [startTime, endTime]
                                    └─ false → WINDOW_CLOSED (410)
                                (5) filename whitelist: `^[A-Za-z0-9_-]+\.(m3u8|ts)$` — 아니면 INVALID_FILE_NAME (400)
                                (6) StreamAddressPort.openSegment(...)
                                      └─ storage.s3-path 기준 `Path.normalize().startsWith(baseDir)` 검증
                                (7) .m3u8 요청이면 ManifestRewriter 로 세그먼트 URL 에 ?t={token} 주입
                                     Cache-Control: no-store
                                (8) .ts 요청은 바이트 스트림 그대로 pipe (StreamingResponseBody)
```

동기화 오프셋은 클라이언트가 `schedule.startTime` 을 받아 `video.currentTime = now − startTime` 으로 맞춘다 (서버 매니페스트 rewrite 는 토큰 주입용, 오프셋 주입 아님).

### 5.5 플로우 E. WebSocket 접속 · 단일 세션 enforcement

```
Browser                                streaming-service
───────                                ──────────────────
STOMP CONNECT ws://.../ws/stream
Header: token={sessionToken}

                              StompAuthChannelInterceptor.preSend(CONNECT)
                                 (1) nativeHeader("token") → StreamTokenPort.parse → sessionId
                                 (2) SessionCachePort.findBySession(sessionId)
                                     └─ 없음 → SESSION_EXPIRED
                                 (3) Schedule.canEnterSession(now) 검사 → 실패 시 WINDOW_CLOSED
                                 (4) EntitlementRepository.find(userId, scheduleId) → 실패 시 NO_ENTITLEMENT
                                 (5) simpSessionAttributes put:
                                     userId, scheduleId, sessionId,
                                     entitlementVerified=true,
                                     ticketId, nickname="관람객#"+ticketId

STOMP SUBSCRIBE /topic/{chat|viewers|state}/schedule/{scheduleId}
STOMP SEND     /app/chat/schedule/{scheduleId}
                              StompAuthChannelInterceptor.preSend(SUBSCRIBE|SEND)
                                 (1) entitlementVerified=true 확인 (없으면 NO_ENTITLEMENT)
                                 (2) destination 의 scheduleId 가 attribute 의 scheduleId 와 일치 확인
                                     └─ 불일치 → SESSION_MISMATCH
                                 (CONNECT 이후 DB/Redis 재조회 없음 — 세션 attribute 캐시)

                              ChatStompController.receive(@MessageMapping 매핑)
                                 (1) ChatService.send(SendChatCommand)
                                       ChatRateLimitPort.tryAcquire(userId) → 실패 시 CHAT_RATE_LIMITED
                                       길이 검사 → 실패 시 CHAT_MESSAGE_TOO_LONG
                                       SimpMessagingTemplate.convertAndSend(
                                         /topic/chat/schedule/{id},
                                         ChatMessage(messageId, nickname, text, sentAt))

첫 입장 시
                              SessionPresenceListener.onConnect
                                 ViewerCachePort.add(scheduleId, userId)
                                 SCARD → /topic/viewers/schedule/{id}

DISCONNECT
                              SessionPresenceListener.onDisconnect
                                 ViewerCachePort.remove(...)
                                 SCARD → /topic/viewers/schedule/{id}
```

**Kick 경로**: 신규 세션 발급 시 (플로우 C 4단계) `KickNotifierPort.notify(userId, DUPLICATE_LOGIN)` 호출 → `/user/{userId}/queue/kick` 으로 `KickMessage` 푸시 + WS 세션 close.

### 5.6 플로우 F. 상태 전이 푸시

Quartz Job 6개가 각 스케줄별로 1회성 등록되어 발동 시 WebSocket 푸시 및 사이드이펙트:

| Job | 발동 | 처리 |
|---|---|---|
| `LobbyOpenJob` | `startTime − 10m` | `/topic/state/schedule/{id}` → `LOBBY_OPEN`. 이 시점부터 입장 허용 |
| `StartingSoonJob` | `startTime − 1m` | `/topic/state/schedule/{id}` → `STARTING_SOON` |
| `StartedJob` | `startTime` | `/topic/state/schedule/{id}` → `STARTED`. 이 시점부터 HLS 서빙 개방 (`canServeHls` true) |
| `EndingSoonJob` | `endTime − 1m` | `/topic/state/schedule/{id}` → `ENDING_SOON` |
| `EndedJob` | `endTime` | `/topic/state/schedule/{id}` → `ENDED`. HLS 서빙 차단. 세션·채팅은 POST 구간(10분) 유지 |
| `ForceExitJob` | `endTime + 10m` | `/topic/state/schedule/{id}` → `FORCE_EXIT`. 해당 스케줄의 모든 세션을 `/user/{userId}/queue/kick(reason=FORCE_EXIT)` 로 퇴장 + WS close + `stream:session:*`·`stream:viewers:schedule:{id}` 삭제 (Redis SCAN + DEL). 단일 노드·수천 세션 이하 가정(ADR 0002). ADR 0012 |

### 5.7 플로우 G. 시청자 수 브로드캐스트

`ViewerCountService` 가 두 트리거로 `/topic/viewers/schedule/{id}` 를 푸시한다.

1. **정기 브로드캐스트** — `@Scheduled(fixedRateString = "${streaming.viewer.broadcast-interval-seconds:5}000")` 로 활성 스케줄 대상 5초 주기.
2. **즉시 브로드캐스트** — `SessionPresenceListener` 가 CONNECT/DISCONNECT 직후 `SCARD` → 즉시 푸시.

카운트 계산: `ViewerCachePort.count(scheduleId)` → `SCARD stream:viewers:schedule:{id}` (O(1)). 재접속 멱등 — 같은 userId 를 여러 번 `SADD` 해도 1건. 대기실·ON_AIR·마무리 구간 모두 적용 (세션 기반, 재생 창과 무관). 단일 노드 전제 — 분산 시 Redis Pub/Sub 도입 필요 (ADR 0002).

---

## 6. 에러 모델

**단일 source of truth 는 `API.md §4 에러 카탈로그`.** 이 표는 동일 내용의 요약.

| Code | HTTP | WS | Retry | 사유 |
|---|---|---|---|---|
| `NO_ENTITLEMENT` | 403 | ERROR | X | 권한 사본에 (userId, scheduleId) 없음 |
| `SCHEDULE_NOT_FOUND` | 404 | ERROR | X | schedule 사본 미적재 |
| `WINDOW_CLOSED` | 410 | ERROR | X | 시간 창 밖 — 세션 발급은 LOBBY~POST 밖, HLS 는 ON_AIR 밖 (`now < startTime − 10m`, `now ≥ endTime + 10m`, `now < startTime`, `now > endTime` 모두 이 코드) |
| `INVALID_TOKEN` | 401 | ERROR | X | JWT 서명 검증 실패 / 형식 오류 |
| `SESSION_EXPIRED` | 401 | ERROR | X | JWT `exp` 지남 또는 Redis 세션 키 부재 |
| `SESSION_MISMATCH` | 409 | ERROR | X | 토큰의 sessionId ≠ Redis 상 현재 userId 의 활성 세션 (단일 세션 enforcement) |
| `INVALID_FILE_NAME` | 400 | — | X | HLS 파일명 정규식 위반 또는 경로 이탈 |
| `SEGMENT_NOT_FOUND` | 404 | — | X | 세그먼트 파일 부재 |
| `STREAM_LOCATION_UNAVAILABLE` | 502 | — | O(1회) | creator-service `/internal/movies/{id}/location` HTTP 호출 실패 |
| `CHAT_RATE_LIMITED` | — | user queue error | X | 유저당 3 msg/sec 초과 (§2.2) |
| `CHAT_MESSAGE_TOO_LONG` | — | user queue error | X | content > 500자 (§2.2) |

**참고 — kick reason 은 에러가 아님**: `/user/queue/kick` payload 의 `reason` 필드는 `SessionKickReason` enum (`DUPLICATE_LOGIN` · `FORCE_EXIT`) — 서버가 세션을 내보낸 **사유** 이지 클라이언트 요청에 대한 에러 응답이 아니다. `DOMAIN-MODEL.md §4` 참조.

---

## 7. 재사용한 기존 코드 · 패턴

- **경로 이탈 방어** (`Path.normalize().startsWith(baseDir)` + 파일명 whitelist 정규식) — creator-service 의 파일 컨트롤러 패턴을 `LocalDirectStreamAddressAdapter` 에 그대로 적용.
- **Content-Type 분기** (`.m3u8` → `application/x-mpegURL`, `.ts` → `video/MP2T`) — 동일 출처.
- **Port 3단 레포 패턴** (`domain/*Repository` 인터페이스 ← `infrastructure/persistence/*RepositoryImpl` ← `*JpaRepository`) — ticket-service 전 서비스 공통.
- **Quartz 동적 등록** (`Trigger.startAt(Date.from(instant))` + `replaceExisting=true`) — ticket-service `QuartzSchedulerAdapter` 패턴 참고.
- **`@RetryableTopic` + `@DltHandler`** — 다른 서비스 Kafka 컨슈머와 동일한 재시도·DLT 구조.
- **JWT 검증은 게이트웨이 전담** — streaming-service 는 게이트웨이 JWT 를 파싱하지 않고 `X-User-Id` 헤더만 신뢰. 자체 토큰(`sessionToken`)은 HLS·WS 전용.