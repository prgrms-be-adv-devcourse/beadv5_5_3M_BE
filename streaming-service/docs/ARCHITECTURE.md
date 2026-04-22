# Architecture (포트폴리오 요약)

MSA 8서비스 중 **다섯 번째** Spring Boot 서비스. 라이브 상영 플랫폼의 "실제 방송 구간"을 담당한다 — 티켓 보유자만 정해진 시간에 동기화된 HLS 스트림을 보고, 그 위에 채팅·동시 시청자 수·재생 상태 신호를 얹는다.

> **TL;DR** — 이 문서만 읽어도 streaming-service 가 왜 필요하고 어떻게 생겼는지 전체 그림이 잡힌다. 세부 명세는 각 문서로 이동.

---

## 1. 해결한 문제

라이브 상영이라는 상품은 VOD 와 기술 스택이 다르다. 4가지를 동시에 풀어야 했다.

| 문제 | 해결 방식 |
|---|---|
| **시간 기반 상태 머신** — 대기실(LOBBY) → 본편(ON_AIR) → 마무리(POST) → 강제 퇴장 전이를 스케줄별로 독립적으로 돌려야 한다. | **Quartz 1회성 Job 6종**(`LobbyOpen` · `StartingSoon` · `Started` · `EndingSoon` · `Ended` · `ForceExit`)을 스케줄 ingest 시점에 등록. 각각 시간에 맞춰 WebSocket 푸시 + 사이드이펙트. |
| **HLS 세그먼트 인증** — 브라우저 `<video>` 태그는 세그먼트 요청에 Authorization 헤더를 싣지 못한다. | **매니페스트 동적 재작성**: 서버가 `.m3u8` 응답 시 모든 세그먼트 라인에 `?t={sessionToken}` 을 주입. 세그먼트 요청은 쿼리 토큰으로 검증. `Cache-Control: no-store` 필수. |
| **계정 공유 차단** — 동일 유저가 여러 기기에서 동시에 보는 것을 막되, 기기 이동은 자연스러워야 한다. | **유저 기준 Redis 키 단일 세션** — 새 세션 발급 시 기존 세션의 Redis 키 제거 + WebSocket `/user/queue/kick` 푸시. Netflix/Disney+ 방식. |
| **상영 종료 후 리소스 정리** — 수많은 장시간 WebSocket 연결이 남으면 서버 부담. | `endTime + 10m` 에 `ForceExitJob` 이 해당 스케줄의 모든 Redis 세션 키를 SCAN + DEL, WebSocket 은 `/user/{userId}/queue/kick` 로 명시적 퇴장 신호. |

---

## 2. 시스템 컨텍스트

```
  ┌─────────────────┐                                       ┌──────────────────┐
  │ creator-service │  ── movie.schedule.confirmed (Kafka)──│                  │
  │                 │                                       │                  │
  │ 영상 업로드       │  ◄── HTTP GET /internal/movies/{id}/location ──        │
  │ HLS 인코딩        │       (videoPath lazy 조회)                             │
  └─────────────────┘                                       │                  │
                                                            │ streaming-svc    │
  ┌─────────────────┐                                       │ (:8088)          │
  │ ticket-service  │  ── ticket.review.authorized ─────────│                  │
  │                 │                                       │                  │
  │ 티켓 판매        │                                       │ ✅ 관람 검증        │
  │ Quartz 스케줄     │                                       │ ✅ HLS 서빙         │
  │ 리뷰 권한 발행    │                                       │ ✅ 채팅·뷰어·상태   │
  └─────────────────┘                                       │                  │
                                                            └────────▲─────────┘
  ┌─────────────────┐       ┌────────────────┐                      │
  │ user-service    │ ────► │ gateway-service│ ─────────────────────┘
  │ 로그인 JWT        │       │ JWT 검증         │  /api/streaming/**
  └─────────────────┘       │ X-User-Id 주입    │  /ws/stream/**
                            └────────────────┘
```

**입력** (두 Kafka 토픽 + 외부 HTTP 한 개 + 게이트웨이 경유 클라이언트 요청):
- `movie.schedule.confirmed` — 스케줄 사본 + Quartz Job 6종 등록
- `ticket.review.authorized` — 관람 권한 적재
- `GET /internal/movies/{id}/location` (to creator-service) — videoPath lazy 조회

**출력**:
- `POST /api/streaming/sessions` — sessionToken · manifestUrl · wsEndpoint 발급
- `GET /api/streaming/{scheduleId}/{file}?t=...` — HLS 매니페스트 (토큰 주입 재작성) · 세그먼트 바이트
- `/ws/stream` — STOMP WebSocket 단일 엔드포인트 (`/topic/chat · /topic/viewers · /topic/state` + `/user/{id}/queue/kick · /queue/errors`)

---

## 3. 핵심 설계 결정 5선

| # | 결정 | 근거 | 참고 |
|---|---|---|---|
| 1 | **단일 노드 운영** (Quartz `isClustered=false`, SimpleBroker, Redis Pub/Sub 미사용) | MVP 부하가 단일 인스턴스 수용량 내. 수평 확장 시 재설계 전제. | [ADR 0002](adr/0002-single-node-deployment.md) |
| 2 | **sessionToken = HS256 JWT + Redis revocation** | 서버 측 강제 무효화 · 단일 세션 enforcement · HLS URL 쿼리 노출 시 피해 제한 — 세 요건을 JWT 단독으로 풀 수 없음. | [ADR 0003](adr/0003-hs256-session-token.md) · [TOKEN.md](TOKEN.md) |
| 3 | **HLS 매니페스트 동적 재작성** | 세그먼트 인증의 유일한 실용적 방법. `<video>` 태그가 쿠키·헤더를 못 붙이므로 URL 쿼리 외 대안 없음. | [ADR 0005](adr/0005-hls-manifest-rewrite.md) |
| 4 | **WebSocket CONNECT 1회 검증 + session attribute 캐시** | WS 가 장시간 연결이라 메시지마다 DB/Redis 재조회하면 비효율. `entitlementVerified=true` + `ticketId`/`nickname` 을 `simpSessionAttributes` 에 put 해놓고 SUBSCRIBE/SEND 는 attribute 만 확인. | [ADR 0010](adr/0010-entitlement-on-connect.md) |
| 5 | **ForceExit 즉시 kick** (graceful 유예 없음) | 상영 종료 시점 + 10분에는 모든 세션을 즉시 내보내고 Redis 키 일괄 DEL. 유예 구간을 두면 복잡도 증가에 비해 UX 이득 미미. | [ADR 0012](adr/0012-immediate-forceexit.md) |

---

## 4. 레이어 구조 (Hexagonal Ports & Adapters)

```
  presentation/  ◄── HTTP/STOMP 진입
     │
     ▼
  application/
     ├── usecase/   (EnterStream · HlsServing · Chat · Lifecycle · ViewerCount)
     ├── service/   (UseCase 구현, @Transactional)
     ├── port/      (출력 포트 9종 인터페이스)
     └── dto/       (record 기반 Command/Result)
     │
     ▼
  domain/       ◄── 엔티티·도메인 포트 인터페이스·enum (프레임워크 의존 0)
     │
     ▲
  infrastructure/  (출력 포트 어댑터)
     ├── persistence/  (JPA, PostgreSQL)
     ├── cache/        (Redis 3종)
     ├── token/        (JJWT HS256)
     ├── address/      (로컬 디스크 HLS 서빙)
     ├── http/         (RestClient → creator-service)
     ├── messaging/    (Kafka 2 토픽 + DLT)
     ├── scheduler/    (Quartz 6 Job)
     └── websocket/    (STOMP 브로커 · Principal · 인증 인터셉터)
```

**핵심 원칙**:
- 도메인 층은 Spring·JPA 포함 모든 프레임워크 의존 없음 (`Schedule.java` 제외 — JPA 애너테이션만, 비즈니스 로직은 순수 Java).
- 모든 출력 의존(DB·Redis·Kafka·HTTP·Quartz·WebSocket)은 포트 인터페이스 뒤에 숨김 — 어댑터 교체만으로 전환 (예: `StreamAddressPort` 를 LocalDirect → SignedUrl → CDN 으로 변경).
- Use case = service implementation 1:1 매핑. Service 는 여러 포트를 조합해 하나의 시나리오를 수행.

---

## 5. 영리한 부분 (Worth Highlighting)

### 5.1 `ManifestRewriter` — 토큰 주입을 Service 가 아닌 어댑터 안으로

`HlsServingService` 는 "권한 검증 + `StreamAddressPort.openSegment` 호출" 만 한다. `.m3u8` 응답일 때 세그먼트 URL 에 토큰을 붙이는 작업은 `ManifestRewriter` 라는 **완전히 분리된 유틸**이 담당. 결과: 서비스는 HLS 인증 방식이 바뀌어도(예: 쿠키 기반으로 전환) 코드 변경 없음.

### 5.2 `StompAuthChannelInterceptor` — WS 인증의 1지점 집중

STOMP `CONNECT/SUBSCRIBE/SEND` 는 모두 `ChannelInterceptor.preSend` 한 곳을 지나간다. CONNECT 에서 토큰 파싱 + 권한 체크 + 세션 attribute put 을 한 번에, 이후 SUBSCRIBE/SEND 는 `entitlementVerified` 플래그만 확인. 메시지당 DB query 없음.

### 5.3 `EnterStreamService` 의 단일 세션 + 지연 조회 합성

신규 세션 발급 시 4가지를 한 트랜잭션에 묶음:
1. 기존 세션 있으면 `KickNotifierPort.notify` + `SessionCachePort.evict`
2. `videoPath` 가 null 이면 `MovieLocationPort.fetch` 로 creator-service 호출 → `schedule.attachVideoLocation`
3. 신규 sessionId UUID + HS256 JWT 발급
4. Redis 2키 (`session:user:*` + `session:id:*`) + manifestUrl 조립 (토큰 URL-encode)

JPA 변경이 커밋되기 전에 외부 HTTP 호출이 끼어있어 실패 시 compensation 이 필요하지만, 이 서비스는 읽기 위주라 ticket-service 의 `CookieCompensationHelper` 같은 복잡한 롤백은 불필요 — DB 트랜잭션 롤백만으로 충분.

### 5.4 `ForceExitJob` 의 배치 정리

`endTime + 10m` 에 발화. 해당 스케줄에 연결된 세션을 Redis `SCAN MATCH stream:session:user:*` 로 훑어 대응하는 `session:id:*` 키와 함께 일괄 DEL, WebSocket 은 `/user/{userId}/queue/kick(reason=FORCE_EXIT)` 푸시 후 close. 단일 노드·수천 세션 이하 가정이라 SCAN 비용 허용.

---

## 6. 확장 포인트 (MVP 이후 고려할 것들)

- **수평 확장** — `isClustered=true` 로 Quartz cluster + Redis Pub/Sub 로 WebSocket broadcast 전파. SimpleBroker → 외부 메시지 브로커(RabbitMQ 등) 교체 필요.
- **CDN 전환** — `StreamAddressPort` 에 `CdnStreamAddressAdapter` 또는 `SignedUrlStreamAddressAdapter` 추가. 프로퍼티 `streaming.address.adapter` 한 줄로 스왑. streaming-service 직접 서빙 부하 0.
- **sessionToken RS256 전환** — CDN edge 에서 서명 검증이 필요해지면 `JjwtStreamTokenAdapter` 옆에 `RsaStreamTokenAdapter` 추가. 공개키만 외부 공유.
- **채팅 backlog** — 현재 휘발성. Redis list (`LPUSH` + `LTRIM`) 로 최근 N건 유지 필요 시 `ChatHistoryPort` 신설 후 `ChatService` 에 주입.
- **관리자 모더레이션** — 지금은 자동 rate limit 만. 관리자 API (`POST /admin/chat/ban`) 및 Redis blacklist 추가 가능.

---

## 7. 기술 스택 요약

- **Java 21** (가상 스레드 활성화 — HLS 블로킹 I/O 처리)
- **Spring Boot 4.0.5** (Jakarta 네임스페이스)
- **PostgreSQL 18** + Hibernate JPA (schedule · entitlement · QRTZ_* 공유)
- **Redis 7** (세션·뷰어·rate limit)
- **Kafka** (토픽 2종 인바운드, DLT 구조)
- **Quartz 2.3** (JDBC store, 단일 노드)
- **JJWT 0.12** (HS256)
- **Spring WebSocket + STOMP 1.2** (SimpleBroker, SockJS 비활성)

---

## 8. 관련 문서

- [DESIGN.md](DESIGN.md) — 상세 설계 · 플로우 다이어그램 7종
- [API.md](API.md) — HTTP + WebSocket 스펙
- [DOMAIN-MODEL.md](DOMAIN-MODEL.md) — 엔티티 · DDL · enum
- [TOKEN.md](TOKEN.md) — sessionToken 깊이 있게
- [MESSAGE-SCHEMAS.md](MESSAGE-SCHEMAS.md) — Kafka payload
- [CONFIG.md](CONFIG.md) — 환경 변수 전체
- [RUNBOOK.md](RUNBOOK.md) — 구동·Smoke Test
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — 운영 문제 모음
- [ONBOARDING.md](ONBOARDING.md) — 신규 팀원 1시간 진입
- [adr/README.md](adr/README.md) — 핵심 결정 5건
