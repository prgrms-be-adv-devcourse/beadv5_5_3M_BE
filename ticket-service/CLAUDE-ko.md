# CLAUDE-ko.md (ticket-service 학습용 한국어 번역본)

> 이 파일은 `CLAUDE.md`(영문 원본, Claude Code가 자동 로드)의 **학습용 한국어 번역본**입니다.
> Claude Code 에이전트는 원본 `CLAUDE.md`를 참조하니, 이 파일은 **사람 학습 목적**으로만 유지하세요.

## 빌드 & 실행 명령어

```bash
./gradlew build                                         # 프로젝트 빌드
./gradlew test                                          # 전체 테스트 실행
./gradlew test --tests "com.example.ticketservice.*"    # 특정 테스트 클래스 실행
./gradlew bootRun                                       # 실행 (기본 프로파일: dev, 포트 8084)
./gradlew clean build                                   # 클린 후 재빌드
```

Docker 빌드는 `build/libs/`에 JAR가 있어야 합니다 — 먼저 `./gradlew build`, 그 다음 `docker build`.

## 아키텍처

헥사고날 아키텍처(Ports & Adapters), Java 21, Spring Boot 4.0.4 기반.

### 계층 구조 (`com.example.ticketservice`)

- **domain/** — 엔티티(`Ticket`, `Schedule`, `Cart`), Enum, 레포지토리 인터페이스. 프레임워크 의존성 없음.
- **application/** — 유스케이스 인터페이스, 서비스 구현, DTO, 도메인 이벤트(records), 출력 포트(`CachePort`, `EventPublisherPort`, `UserPort`, `SchedulerPort`, batch 포트들). 서비스는 오직 포트 인터페이스에만 의존.
- **infrastructure/** — 포트 구현: JPA 레포지토리, `RedisCacheAdapter`, `KafkaEventPublisher`, `QuartzSchedulerAdapter`, `UserClient` (RestClient), Spring Batch 설정, `@TransactionalEventListener` 핸들러.
- **presentation/** — REST 컨트롤러(`TicketController`, `CartController`, `QueueController`, `JobTestController`), `GlobalExceptionHandler`.
- **common/** — 공용 예외(`TicketException`, `ScheduleException`, `QueueException`), `ErrorCode` Enum, `PageResult`.

### 레포지토리 3-계층 패턴

```
domain.repository.TicketRepository (포트 인터페이스)
  ↑ 구현
infrastructure.persistence.impl.TicketRepositoryImpl
  ↑ 위임
infrastructure.persistence.TicketJpaRepository (Spring Data JPA)
```

### 핵심 도메인 흐름

- **Ticket 생명주기:** `RESERVED → CONFIRMED → provideFlag=true`.
- **Schedule 생명주기:** `CART → IN_PROGRESSING → TICKETING → STREAMING → FINISH`.
- **Schedule 생성:** `movie.schedule.confirmed` Kafka 컨슈머 → `Schedule` 엔티티 생성 (아직 티켓 없음; 좌석 수는 Schedule에 저장).
- **장바구니 단계:** 사용자가 장바구니에 담음 (DB 레코드 + Redis 카운터 `cart:count:schedule:{id}`). Redis INCR/DECR은 `CartUpdatedEvent`의 AFTER_COMMIT으로 실행 — DB 롤백 시 카운터 오염을 방지. T-24h 시점에 `CartCloseQuartzJob` 발동: 수요 < 재고 → 모두 RESERVED 티켓 일괄 생성; 아니면 → 선착순 부분 예약, 나머지는 삭제.
- **대기열/티켓팅 단계:** `TicketingStartQuartzJob`이 `ticketingTime`에 발동 → 미결제 RESERVED 티켓 정리, TICKETING으로 전이, `TicketingStartedEvent` 발행. AFTER_COMMIT 핸들러(`TicketEventListener.handleTicketingStarted`)가 Redis 키를 시딩: `stock`, `paying`, `seats`, `cookie`, `startTime`. 대기열 엔트리는 sorted set `queue:schedule:{id}` 사용. `paying:schedule:{id}` 카운터가 동시 구매자 수를 추적.
- **대기열 드레인:** 재고가 복구되면(환불/결제 실패) `queue.drain` Kafka 토픽이 `key=scheduleId`로 발행됨. `QueueDrainConsumer`(concurrency=4, group=`queue-drain-group`)가 소비해 `checkAndProcess()`를 동기 호출 — 과거 `@Async` 방식이 야기했던 스레드풀 고갈을 대체.
- **자율결제:** `SelfPaymentService`로 RESERVED → CONFIRMED; `UserPort`(HTTP)로 쿠키 차감. 잔액 부족 시 `queue.drain`을 직접 발행 (롤백 경로라 AFTER_COMMIT 사용 불가).
- **환불:** CONFIRMED 티켓 삭제; `TicketRefundedEvent` AFTER_COMMIT 핸들러가 `UserPort.refundCookie()`(HTTP)를 호출 후 Redis 재고 복구 + `queue.drain` 트리거.
- **일일 결제 배치:** `ticketProvideJob` 새벽 1시 — CONFIRMED + 미결제 티켓의 `provideFlag=true`를 일괄 세팅, `KafkaBulkEventPublisher`(snappy 압축)로 `ticket.provide` 토픽 발행.

### 이벤트 드리븐 정합성

서비스는 `@Transactional` 메서드 안에서 Spring `ApplicationEvent`를 발행. `infrastructure/event/TicketEventListener`의 `@TransactionalEventListener(phase = AFTER_COMMIT)` 핸들러가 Redis 쓰기·HTTP 호출·Kafka 발행을 실행. 이를 통해 **부수효과는 DB 커밋 이후에만 실행됨**을 보장 — DB 롤백 = 이벤트 미발행 = Redis/HTTP stale 상태 없음.

`TicketEventListener`의 주요 핸들러:
- `handleTicketingStarted`: Redis 5개 키 시딩 (stock/paying/seats/cookie/startTime)
- `handleTicketPaid` / `handleTicketRefunded`: `queue.drain` Kafka 메시지 발행
- `handleTicketRefunded`: Redis/Kafka 전에 `UserPort.refundCookie()` HTTP 호출
- `handleCartUpdated`: `cart:count:schedule:{id}`에 INCR 또는 DECR

### 핵심 동시성 패턴

- **HTTP + DB 보상:** 외부 HTTP 호출(예: `UserPort.deductTicketFee`) 후 `CookieCompensationHelper.registerRollbackRefund()`가 `TransactionSynchronization.afterCompletion()` 콜백을 등록 — DB 트랜잭션이 롤백되면 쿠키를 환불. `SelfPaymentService`와 `QueuePurchaseProcessor`에서 사용.
- **재고 복구 → queue.drain 연결:** Redis 재고가 복구될 때마다(환불 또는 결제 실패) `queue.drain` Kafka 메시지를 **반드시** 발행해 대기 사용자를 트리거.
- **Redis 키 TTL:** 티켓팅 시작 시 시딩된 키들은 `startTime - 10min`에 자동 만료. 수동 정리 불필요.
- **Queue drain 컨슈머:** `QueueDrainConsumer`(concurrency=4, group=`queue-drain-group`)가 대기열을 동기 처리. 내부적으로 `processWindowParallel()`이 별도 `queueExecutor` 스레드풀(core=4, max=8, `CallerRunsPolicy`) 사용.

### 출력 포트 → 어댑터

| 포트 | 어댑터 |
|------|--------|
| `EventPublisherPort` | `KafkaEventPublisher` (`AbstractKafkaPublisher` 상속) |
| `CachePort` | `RedisCacheAdapter` |
| `UserPort` | `UserClient` (RestClient) |
| `SchedulerPort` | `QuartzSchedulerAdapter` |
| `TicketProvideBatchPort` | `TicketProvideBatchAdapter` |
| `TicketCleanupBatchPort` | `TicketCleanupBatchAdapter` |

### Redis 키 규칙

- `cart:count:schedule:{scheduleId}` — CART 단계 수요 카운터
- `stock:schedule:{scheduleId}` — TICKETING 중 가용 좌석 수
- `queue:schedule:{scheduleId}` — 대기 사용자 sorted set (score = timestamp)
- `paying:schedule:{scheduleId}` — 현재 구매 플로우에 있는 사용자 수
- `seats:schedule:{scheduleId}` — 티켓팅 시작 시 캐싱된 총 좌석 수 (ticketNum 계산용, DB 조회 불필요)
- `cookie:schedule:{scheduleId}` — 티켓팅 시작 시 캐싱된 쿠키 단위 티켓 가격
- `startTime:schedule:{scheduleId}` — 티켓팅 시작 시 캐싱된 이벤트 시작 시각 (ISO-8601, 대기열 TTL용)

### Kafka 토픽

| 토픽 | 방향 | 목적 |
|------|------|------|
| `movie.schedule.confirmed` | 수신 | Schedule 엔티티 생성 |
| `ticket.paid` | 발신 | 자율결제 완료 후 |
| `ticket.refunded` | 발신 | 환불 완료 후 |
| `cart.closed` | 발신 | 장바구니 마감 후 (Case A: 전체 / Case B: 부분) |
| `ticketing.started` | 발신 | 티켓팅 단계 시작 후 |
| `ticket.provide` | 발신 | 일일 배치 결제 알림 |
| `queue.drain` | 내부 | `QueueDrainConsumer`로 `checkAndProcess()` 트리거 (@Async 대체) |
| `queue.terminated` | 내부 | 대기열 종료 (재고 소진) |
| `ticket.review.authorized` | 발신 | CONFIRMED 티켓 보유자 리뷰 권한 부여 |

토픽 이름 상수는 `infrastructure/messaging/KafkaTopics.java`에 중앙 관리. Redis 키 프리픽스는 `application/constants/RedisKeys.java`에 정리.

### Quartz Job

| Job | 트리거 | 작업 |
|-----|--------|------|
| `CartCloseQuartzJob` | 티켓팅 T-24h | 장바구니 마감, RESERVED 일괄 또는 부분 생성 |
| `TicketingStartQuartzJob` | `schedule.ticketingTime` | 정리, Redis 재고 초기화, TICKETING으로 전이 |
| `StreamingStartQuartzJob` | `schedule.startTime` | Schedule을 STREAMING으로 전이 |
| `StreamingFinishQuartzJob` | `schedule.endTime` | Schedule을 FINISH로 전이 |
| `ReviewAuthQuartzJob` | `schedule.startTime` | CONFIRMED 티켓 보유자 대상 리뷰 권한 이벤트 발행 |

## 프로파일

- `dev` (기본): 로컬 DB, DEBUG 로깅, Swagger `http://localhost:8084/swagger-ui.html`, Quartz 비클러스터(스레드 5개)
- `prod`: 환경변수 기반 (`DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `CLIENT_USER_BASE_URL`, `SERVER_PORT`), WARN 로깅, Swagger 비활성, Quartz 클러스터(스레드 10개)

## 테스트

**Unit/integration 테스트**는 H2 in-memory DB(`create-drop`) 사용. Kafka와 Batch 자동설정은 테스트에서 제외(`src/test/resources/application.yaml`).

**E2E 테스트**(`e2e/` 디렉터리): Python 스위트(`e2e_test.py`)에 5개 시나리오 — Case A 일괄 예약, Case B 부분, SOLD_OUT, 잔액 부족, 500명 동시성. 서비스 기동 필요. 테스트 데이터: `schedule_events.json`, `test_users.sql`.

## 외부 의존성

- **PostgreSQL** (영속성) — 포트 5432, DB: `ticket_db`
- **Redis** (대기열, 재고 카운터, 장바구니 수요, 리뷰 권한) — 포트 6379
- **Kafka** (서비스 간 이벤트 드리븐 통신) — consumer group: `ticket-service`
- **User service** — `POST /internal/users/deduct/cookie`와 `POST /internal/users/refund/cookie`