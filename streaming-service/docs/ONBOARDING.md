# Onboarding (신규 진입 1시간 가이드)

streaming-service 에 처음 기여하는 사람이 **1시간 안에** 로컬 구동 + 코드 맥락 파악을 완료하는 것을 목표로 한다.

---

## 1. Day 1 체크리스트 (읽기 순서)

- [ ] **(5 분)** [ARCHITECTURE.md](ARCHITECTURE.md) — 이 서비스가 뭘 하는 건지 한 장으로 이해
- [ ] **(10 분)** [OVERVIEW.md](OVERVIEW.md) — 주변 서비스와 관계 · 라이프사이클 상태 표
- [ ] **(15 분)** [DESIGN.md §4~§5](DESIGN.md) — 레이어 구조 · Redis 키 · 플로우 A/C/D/E 다이어그램
- [ ] **(15 분)** [RUNBOOK.md](RUNBOOK.md) — 로컬 구동 + Smoke Test 직접 수행
- [ ] **(10 분)** 아래 **§2 코드 투어** 따라가며 실제 파일 열어보기
- [ ] **(5 분)** [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 훑어보기 (어떤 문제가 있을 수 있는지 인지)

끝나면 팀 채널에 "구동 성공 + sessionToken 발급까지 확인" 알리면 된다.

---

## 2. 코드 투어 — 3개 주요 흐름

### 2.1 세션 발급 흐름 (HTTP)

```
POST /api/streaming/sessions
    │
    ▼
presentation/StreamingSessionController.java
    │  @RequestHeader("X-User-Id") UUID userId
    │  + SessionIssueRequest(scheduleId)
    ▼
application/usecase/EnterStreamUseCase      (인터페이스)
    ▼
application/service/EnterStreamService      (구현)
    │  1. scheduleRepository.findById     → ScheduleRepository 포트
    │  2. schedule.canEnterSession(now)   → 도메인 메서드
    │  3. entitlementRepository.find      → EntitlementRepository 포트
    │  4. sessionCache.findByUser         → SessionCachePort
    │     → 있으면 kickNotifier.notify    → KickNotifierPort
    │  5. movieLocation.fetch (필요 시)   → MovieLocationPort
    │     → schedule.attachVideoLocation  → 도메인 메서드
    │  6. streamToken.issue               → StreamTokenPort (JJWT)
    │  7. sessionCache.put                → SessionCachePort
    │  8. streamAddress.resolveManifestUrl → StreamAddressPort
    ▼
SessionIssueResult { sessionToken, sessionId, manifestUrl, wsEndpoint, expiresAt, schedule }
```

**읽어볼 파일**:
- `presentation/StreamingSessionController.java`
- `application/service/EnterStreamService.java`
- `application/port/*.java` (9개 포트 인터페이스 전체 훑기)
- `infrastructure/token/JjwtStreamTokenAdapter.java` (HS256 실구현)
- `infrastructure/cache/RedisSessionCacheAdapter.java` (2키 관리)

### 2.2 HLS 세그먼트 제공 흐름

```
GET /api/streaming/{scheduleId}/{file}?t={token}
    │
    ▼
presentation/HlsController.java
    │  @RequestParam("t") String token
    ▼
application/service/HlsServingService.java
    │  1. streamToken.parse(token)        → sessionId
    │  2. sessionCache.findBySession      → meta
    │  3. meta.scheduleId 일치 확인
    │  4. schedule.canServeHls(now)       → ON_AIR 창 재확인
    │  5. 파일명 whitelist 정규식
    │  6. streamAddress.openSegment       → HlsResource(InputStream, ContentType, Length)
    │  7. .m3u8 이면 ManifestRewriter.rewrite(in, scheduleId, token)
    │     → 세그먼트 라인에 ?t={token} 주입
    ▼
StreamingResponseBody 로 출력 (Cache-Control: no-store for .m3u8)
```

**읽어볼 파일**:
- `presentation/HlsController.java`
- `application/service/HlsServingService.java`
- `infrastructure/address/LocalDirectStreamAddressAdapter.java` (path traversal 방어 필수)
- `infrastructure/websocket/ManifestRewriter.java`

### 2.3 WebSocket CONNECT 흐름

```
ws://.../ws/stream
STOMP CONNECT headers: { token: "..." }
    │
    ▼
config/WebSocketConfig.java
    │  (STOMP endpoint + ChannelInterceptor 등록)
    ▼
infrastructure/websocket/StompAuthChannelInterceptor.preSend(CONNECT)
    │  1. nativeHeader("token") 추출
    │  2. streamToken.parse                → sessionId
    │  3. sessionCache.findBySession       → meta
    │  4. scheduleRepository.findById      → schedule
    │  5. schedule.canEnterSession(now)
    │  6. entitlementRepository.find       → entitlement
    │  7. simpSessionAttributes put:
    │       userId, scheduleId, sessionId,
    │       entitlementVerified=true,
    │       ticketId, nickname="관람객#"+ticketId
    │  8. acc.setUser(new StreamingPrincipal(userId.toString()))
    ▼
이후 SUBSCRIBE/SEND 는 preSend(SUBSCRIBE|SEND) 분기에서 entitlementVerified 플래그만 확인.
(destination 의 scheduleId 가 attribute 의 scheduleId 와 일치하는지도 정규식으로 체크)
```

**읽어볼 파일**:
- `config/WebSocketConfig.java`
- `infrastructure/websocket/StompAuthChannelInterceptor.java` ⭐ (핵심)
- `infrastructure/websocket/ChatStompController.java` (@MessageMapping)
- `infrastructure/websocket/SessionPresenceListener.java` (뷰어 카운트 트리거)

---

## 3. 이 repo 만의 관용구

### 3.1 도메인 포트 3단 패턴

```
domain/ScheduleRepository.java          (인터페이스)
    ▲
infrastructure/persistence/ScheduleRepositoryImpl.java  (@Repository, 위 인터페이스 구현)
    │ 내부적으로 호출
infrastructure/persistence/ScheduleJpaRepository.java   (Spring Data JPA, extends JpaRepository)
```

서비스는 `ScheduleJpaRepository` 를 직접 주입하지 않는다. 항상 `ScheduleRepository` (포트) 를 주입.

### 3.2 `application/constants/` 상수 컬렉션

Redis 키 패턴은 `RedisKeys.sessionByUser(userId)` / WebSocket destination 은 `WsDestinations.chatTopic(scheduleId)` 같은 유틸 메서드로 생성. 문자열 리터럴을 코드에 박지 말 것.

### 3.3 `ErrorCode` enum + 서비스별 Exception

도메인 예외는 모두 `ScheduleException.notFound()` · `SessionException.windowClosed()` 같은 factory 메서드로 생성. 내부적으로 `ErrorCode` enum 을 참조해 `HttpStatus` + 메시지를 주입. `GlobalExceptionHandler` 가 일괄 `ErrorResponse.of(errorCode)` 변환.

### 3.4 엔티티 상태 변경은 메서드로만

`schedule.videoPath = x` 식의 필드 직접 대입 금지. `schedule.attachVideoLocation(path, runningTime)` 같은 도메인 메서드 사용. 메서드 안에 불변식 체크 (예: 이미 set 이면 `IllegalStateException`) 를 같이 둠.

### 3.5 Kafka payload 는 record

`infrastructure/messaging/dto/*Payload.java` 는 전부 record. 역직렬화는 `KafkaMessageUtil.deserialize(message, Class<T>)` 로 리스너 내부에서 명시. `JsonDeserializer` 미사용.

### 3.6 Quartz Job 은 `schedule-{scheduleId}-{kind}` 네이밍

JobKey/TriggerKey 는 `"schedule-42-lobby-open"` 형식. `replaceExisting=true` 로 재등록 — 같은 schedule 에 대한 Quartz 재-초기화가 멱등적으로 작동.

---

## 4. 기여 워크플로

### 4.1 브랜치·커밋

- 브랜치: `feature/streaming/<kebab-case-name>`, `fix/streaming/<name>`, `chore/streaming/<name>`
- 커밋: `feat(streaming): ...` / `fix(streaming): ...` (scope 은 항상 `streaming`)
- 커밋당 한 가지 주제, 메시지는 "왜" 중심

### 4.2 PR 체크리스트 (본인 self-review)

- [ ] 엔티티 필드 직접 수정 없음 (도메인 메서드만)
- [ ] 새 포트 추가 시 어댑터도 함께
- [ ] `application/port/` vs `application/usecase/` 구분 — 출력 포트 vs 입력 UseCase
- [ ] 에러는 `ErrorCode` enum 에 등록 후 서비스별 Exception factory 로만 throw
- [ ] HLS/WS 응답 헤더 (`Cache-Control: no-store`, `Content-Type`) 정확한지
- [ ] Kafka listener 에 `@RetryableTopic` + `@DltHandler` 쌍
- [ ] 테스트 (`ScheduleTest` 류) — 상태 경계 (CLOSED/LOBBY/ON_AIR/POST/FINISHED) 로직 변경 시 반드시
- [ ] `docs/` 영향 있으면 해당 문서도 같이 수정

---

## 5. 함정 (Pitfalls)

### 5.1 매니페스트 캐싱 금지

`.m3u8` 응답에 반드시 `Cache-Control: no-store`. 캐시되면 토큰이 만료 후에도 공유되어 유출 위험.

### 5.2 Quartz Job 시각 변경 시 `replaceExisting=true` 누락

기존 trigger 와 충돌하면 Quartz 는 silent fail. `SchedulerPort.scheduleLifecycle` 은 항상 `replaceExisting=true` 로 재등록하도록 되어 있으나, 새 Job 추가 시 같은 원칙 적용 필수.

### 5.3 Redis 키 TTL 불일치

3개 키(`session:user:*`, `session:id:*`, `viewers:*`)는 모두 `endTime + 10m` 까지 TTL 동일. 하나만 TTL 짧게 걸면 "session 은 살아있는데 viewer 에서는 빠진" 상태 발생.

### 5.4 WebSocket SUBSCRIBE 시 destination scheduleId 검증

`/topic/chat/schedule/99` 를 구독해도 CONNECT 시 attribute 에 담긴 scheduleId 가 42 라면 `SESSION_MISMATCH`. 정규식 `^/(?:topic|app)/[a-z]+/schedule/(\d+)(?:/|$)` 가 destination 에서 scheduleId 추출 — URL 구조 변경 시 같이 수정.

### 5.5 STOMP CONNECT `token` 헤더를 URL 쿼리로 착각

WebSocket 핸드셰이크 URL 에 `?t=...` 를 넣는 것은 HLS 이야기. STOMP 는 CONNECT **프레임의 header** 로 전달. `@stomp/stompjs` 는 `connectHeaders: { token: ... }` 옵션.

### 5.6 JPA `@Transactional` 밖에서 엔티티 수정

`EnterStreamService` 에 `@Transactional` 이 클래스 레벨로 걸려 있지만, non-service 클래스(Quartz Job 등)에서 엔티티 수정 시 별도 트랜잭션 보장 필요. 보통 UseCase 재호출로 해결.

### 5.7 ChatMessage `content` 필드 vs `text`

`ChatMessage` record 의 실제 필드명은 `content`. `text` 아님. 클라이언트와 맞출 때 주의.

---

## 6. 자주 묻는 질문

**Q. 왜 SockJS 안 쓰나?**
A. 최신 브라우저 전제 (fallback 미지원). SockJS 추가 시 `WebSocketConfig.addEndpoint("/ws/stream").withSockJS()` 한 줄로 활성화 가능하지만 토큰 전달 방식을 URL 쿼리로 바꿔야 해서 현재 STOMP header 정책(액세스 로그에 토큰 노출 방지)과 충돌.

**Q. sessionToken 을 로컬스토리지에 저장해도 되나?**
A. 비권장. XSS 시 탈취. React state 등 메모리 보관 → 새로고침 시 `POST /api/streaming/sessions` 재호출로 재발급 (기존 세션 kick 후 신규). [TOKEN.md §7.3](TOKEN.md) 참조.

**Q. 상영 끝난 영상을 다시 볼 수 있나?**
A. 아니. 동기화 라이브 전제. `endTime` 지나면 HLS 서빙 거부. VOD 기능 원하면 별도 설계 필요.

**Q. 개발 중에 `startTime-10m ~ endTime` 창을 수동 조작하려면?**
A. DB 직접 UPDATE: `UPDATE schedule SET start_time=NOW() - INTERVAL '5 minutes', end_time=NOW() + INTERVAL '1 hour' WHERE schedule_id=42;` — 다만 Quartz trigger 는 이미 등록되어 있으므로 재 ingest (Kafka 재발행) 필요.

---

## 7. 도움 받을 곳

- 구동 실패 → [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 의 해당 증상
- 설계 의도 궁금 → [DESIGN.md §3](DESIGN.md) 결정 표 + [adr/](adr/) 5개 ADR
- API 호출 스펙 궁금 → [API.md](API.md)
- 환경변수 설정 → [CONFIG.md](CONFIG.md)
