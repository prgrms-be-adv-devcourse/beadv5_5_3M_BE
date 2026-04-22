# Stage 4 — 이벤트 리스너 (AFTER_COMMIT 사이드 이펙트)

> **목표**: 이 서비스의 **정합성 뼈대**인 `@TransactionalEventListener(AFTER_COMMIT)` 패턴을 실제 리스너 5개로 확인한다.
> Stage 2에서 Service가 "`publishEvent`만 하고 끝" 이었다면, 여기서는 **누가·언제·무엇을** 실제로 실행하는지를 본다.
> **예상 소요**: 1.5일

---

## 0. 이벤트 records 6종

`application/event/` 전체 — 모두 **Java 16+ record**, 불변·직렬화 안전.

| 이벤트 | 발행처 | 수신 리스너 | 역할 |
|--------|--------|-------------|------|
| `CartUpdatedEvent(scheduleId, userId, added)` | `CartService` | `TicketEventListener.handleCartUpdated` | Redis `cart:count` INCR/DECR |
| `CartClosedEvent(scheduleId, caseType, seats, userIds)` | `CartCloseService` | `TicketEventListener.handleCartClosed` | Kafka `cart.closed` 발행 |
| `TicketingStartedEvent(scheduleId, remaining, seats, cookie, startTime)` | `TicketingStartService` | `TicketEventListener.handleTicketingStarted` | Redis 5키 세팅 + Kafka `ticketing.started` |
| `TicketPaidEvent(ticketId, scheduleId, userId, cookie)` | `SelfPayment`·`QueuePurchase` | `TicketEventListener.handleTicketPaid` | Kafka `ticket.paid` + `queue.drain` |
| `TicketRefundedEvent(ticketId, scheduleId, userId, cookie)` | `RefundService` | `TicketEventListener.handleTicketRefunded` | HTTP 환불 + stock INCR + Kafka 2종 |
| `ScheduleInitializedEvent(scheduleId, ticketingTime, startTime, endTime)` | `ScheduleEventConsumer` (Kafka in) | `ScheduleEventListener.handleScheduleInitialized` | Quartz 5 Job 등록 + `cart:count` 0 초기화 |

### 페이로드 설계 원칙

- **ID + 최소 필드**. 리스너가 필요로 하는 값만 (예: `cookie` 금액은 환불에 필요 → 포함).
- **엔티티 참조 안 담음** — LAZY 로딩 이슈 + 영속성 컨텍스트 종료 이후 실행되는 핵심 이유.
- Record 불변 → 여러 리스너가 동시 수신해도 안전.

---

## 1. AFTER_COMMIT 패턴의 핵심

### 왜 필요한가

```
@Transactional
void doBusiness() {
    ticket.pay();          // 1. DB 상태 변경
    cachePort.increment(...); // 2. Redis 쓰기 ← DB 롤백 시 Redis 잔류!
    kafkaPublish(...);      // 3. Kafka 발행 ← DB 롤백 시 가짜 이벤트 유출!
}
```

→ 2, 3은 Spring의 트랜잭션 매니저가 롤백해 줄 수 없음 (외부 시스템).
→ 해결: 2, 3을 **커밋 성공 이후에만** 실행.

### Spring의 해결책

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(SomeEvent event) {
    // 여기 코드는 오직 DB 커밋이 성공한 후에만 호출됨
}
```

- `@EventListener`는 동기 호출 → 트랜잭션 내부에서 실행 → 롤백돼도 이미 Redis/Kafka 바뀜.
- `@TransactionalEventListener(AFTER_COMMIT)` → Spring이 `TransactionSynchronization.afterCommit` 훅으로 연기.

---

## 2. TicketEventListener — 5개 핸들러

파일 `infrastructure/event/TicketEventListener.java`. 모든 메서드가 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

### 2.1 `handleTicketingStarted` — Redis 5키 원샷 세팅

`infrastructure/event/TicketEventListener.java:87-101`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleTicketingStarted(TicketingStartedEvent event) {
    Duration ttl = Duration.between(LocalDateTime.now(), event.startTime().minusMinutes(10));
    if (!ttl.isNegative() && !ttl.isZero()) {
        cachePort.setCounter(RedisKeys.STOCK + event.scheduleId(), event.remaining(), ttl);
        cachePort.setCounter(RedisKeys.PAYING + event.scheduleId(), 0, ttl);
        cachePort.setCounter(RedisKeys.SEATS + event.scheduleId(), event.seats(), ttl);
        cachePort.setCounter(RedisKeys.COOKIE + event.scheduleId(), event.cookie(), ttl);
        cachePort.set(RedisKeys.START_TIME + event.scheduleId(), event.startTime().toString(), ttl);
    }
    eventPublisherPort.publish(KafkaTopics.TICKETING_STARTED, event.scheduleId().toString(),
            new TicketingStartedMessage(event.scheduleId()));
}
```

**주목할 점**:
- **TTL 계산**: `startTime - 10분`까지 살아있음. 스트리밍 10분 전부터는 대기열·환불 막음 → 키 불필요.
- **TTL이 음수·0이면 skip** — 이미 startTime 지났거나 10분 이내면 아예 세팅하지 않음 (엣지 케이스).
- 모든 키를 한 번에 세팅 → **이 시점이 "티켓팅 모드 진입" 시그널**. 이후 TICKETING 상태에서 대기열·재고가 동작함.

### 2.2 `handleTicketPaid` — 단순 2번 발행

`infrastructure/event/TicketEventListener.java:37-44`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleTicketPaid(TicketPaidEvent event) {
    eventPublisherPort.publish(KafkaTopics.TICKET_PAID, event.ticketId().toString(),
            new TicketPaidMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
    eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, event.scheduleId().toString(),
            new QueueDrainMessage(event.scheduleId()));
}
```

**주목할 점**:
- `ticket.paid` (외부 알림) + `queue.drain` (내부 트리거) 2발.
- `queue.drain`은 SelfPayment 성공 시에도 발행 → Case A 결제 완료가 대기열에 어떤 영향? → 없음 (`drainQueue`가 `queueSize == 0` 조건으로 즉시 반환).
- 즉 `queue.drain`은 "**재고 변화가 있을 수 있다**"라는 범용 트리거. 필요 없으면 리스너 쪽에서 자동 무시.

### 2.3 ★ `handleTicketRefunded` — 3중 try-catch

`infrastructure/event/TicketEventListener.java:46-78`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleTicketRefunded(TicketRefundedEvent event) {
    // 1. 쿠키 환불 — DB 커밋 후 실행하여 이중 환불 방지
    //    실패해도 stock 복구/Kafka 발행은 반드시 진행해야 대기자 처리가 지연되지 않음
    try {
        userPort.refundCookie(new RefundCookieRequest(event.ticketId(), event.cookie(), event.userId()));
    } catch (Exception e) {
        log.error("쿠키 환불 실패 - ticketId={}, userId={}, 수동 처리 필요", event.ticketId(), event.userId(), e);
    }

    // 2. 티켓팅 진행 중이면 Redis 재고 복구 (stock 키가 없으면 INCR 스킵)
    try {
        String stockKey = RedisKeys.STOCK + event.scheduleId();
        if (cachePort.exists(stockKey)) {
            cachePort.increment(stockKey);
            eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, event.scheduleId().toString(),
                    new QueueDrainMessage(event.scheduleId()));
        }
    } catch (Exception e) {
        log.error("stock 복구 실패 - scheduleId={}", event.scheduleId(), e);
    }

    // 3. Kafka ticket.refunded 발행
    try {
        eventPublisherPort.publish(KafkaTopics.TICKET_REFUNDED, event.ticketId().toString(),
                new TicketRefundedMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
    } catch (Exception e) {
        log.error("ticket.refunded 발행 실패 - ticketId={}", event.ticketId(), e);
    }
}
```

### ★ 3중 try-catch 설계 의도

| # | 실패 시 영향 | 다른 블록에 영향? |
|---|--------------|--------------------|
| 1. refundCookie (HTTP) | 유저가 쿠키 못 돌려받음 | **다음 블록은 반드시 진행**해야 대기자가 구매 기회 얻음 |
| 2. stock INCR + queue.drain | 환불된 좌석이 대기자에게 가지 않음 | Kafka 발행은 독립적으로 진행 |
| 3. ticket.refunded Kafka | 다른 서비스(정산 등) 알림 누락 | — |

→ **한 블록의 실패가 다음 블록을 막지 않음**. 각각의 실패는 독립적으로 처리·복구 대상.

### ★ 함의 — "부분 실패"의 시스템 상태

block 1만 실패했다면:
- 티켓은 이미 DB DELETE ✅
- 쿠키는 차감 상태 유지 🛑 (유저가 수동 문의해야 복구)
- Redis stock INCR ✅
- Kafka `ticket.refunded` 발행 ✅

→ 다른 서비스들은 "환불됐다"고 믿고 처리하지만 실제로는 쿠키 환불이 미완료. **이 서비스는 로그만 남김** → 모니터링·알람·재시도 워커가 실무 안전망.

### 2.4 `handleCartClosed` — 단일 Kafka 발행

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleCartClosed(CartClosedEvent event) {
    eventPublisherPort.publish(KafkaTopics.CART_CLOSED, event.scheduleId().toString(),
            new CartClosedMessage(event.scheduleId(), event.caseType(), event.seats(), event.userIds()));
}
```

**주목할 점**:
- `caseType`을 페이로드에 담음 → 수신 서비스(user-service 등)가 "Case A vs B"를 구분해 알림 등 처리.
- **대기열 ZADD는 여기가 아니라** user-service 컨슈머 측에서 담당 (책임 분리).

### 2.5 `handleCartUpdated` — 단순 INCR/DECR

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleCartUpdated(CartUpdatedEvent event) {
    String key = RedisKeys.CART_COUNT + event.scheduleId();
    if (event.added()) {
        cachePort.increment(key);
    } else {
        cachePort.decrement(key);
    }
}
```

**주목할 점**:
- Service의 `addToCart`/`removeFromCart`가 DB 작업만 하고 이 리스너가 Redis 조작 → **DB ↔ Redis 정합성** 보장.
- 가정: `cart:count` 키는 `ScheduleEventListener`가 Schedule 생성 시 0으로 세팅해 둠 (기본값 0 확보).

---

## 3. ScheduleEventListener — Schedule 생성 훅

`infrastructure/event/ScheduleEventListener.java:25-40`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleScheduleInitialized(ScheduleInitializedEvent event) {
    schedulerPort.scheduleCartCloseJob(event.scheduleId(), event.ticketingTime().minusHours(24));
    schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
    schedulerPort.scheduleReviewAuthJob(event.scheduleId(), event.startTime());
    schedulerPort.scheduleStreamingStartJob(event.scheduleId(), event.startTime());
    schedulerPort.scheduleStreamingFinishJob(event.scheduleId(), event.endTime());

    Duration ttl = Duration.between(LocalDateTime.now(), event.ticketingTime().minusHours(24));
    if (!ttl.isNegative() && !ttl.isZero()) {
        cachePort.setCounter(RedisKeys.CART_COUNT + event.scheduleId(), 0, ttl);
    }
}
```

**주목할 점**:
- Schedule DB 커밋 후에야 **5개 Quartz Job 한 번에 등록**. DB 롤백 시 Job 없음 보장.
- `cart:count` 초기값 0을 TTL과 함께 세팅. TTL = CartClose 시점까지.

---

## 4. ★ AFTER_COMMIT을 @EventListener로 바꾸면 생기는 버그

가정: `handleTicketingStarted`가 `@TransactionalEventListener(AFTER_COMMIT)` 대신 `@EventListener`였다면?

### 시나리오

1. `TicketingStartService.execute`가 `ticketCleanupBatchPort.run()` 호출.
2. batch 완료 후 `schedule.startTicketing()` 실행 (in-memory 상태 전이).
3. `ticketRepository.save(schedule)`.
4. `eventPublisher.publishEvent(new TicketingStartedEvent(...))` — **@EventListener는 여기서 즉시 동기 호출**.
5. 리스너가 Redis 5개 키를 세팅 + Kafka 발행 ✅
6. Service 메서드 종료 시점에 커넥션 유실로 **DB 커밋 실패 (롤백)**.

### 결과

| 시스템 | 상태 |
|--------|------|
| DB `schedule.status` | **여전히 IN_PROGRESSING** (롤백) |
| Redis `stock`, `paying`, ... | **TICKETING 중이라 생각하고 세팅됨** 🛑 |
| Kafka `ticketing.started` | **외부 서비스에 잘못된 신호 유출** 🛑 |

→ 대기열에 사용자 입장해도 DB는 "아직 아냐" → 정합성 붕괴.

### 다른 예 (`handleCartUpdated`)

- 유저가 `addToCart` 호출 → DB insert 시도.
- `@EventListener` 즉시 호출 → `cart:count` INCR.
- DB insert가 중복 제약 위반으로 예외 → 롤백.
- 결과: DB Cart row 없음, Redis는 1 증가 **→ 수요 stale**.

---

## 5. 설계 교훈 3가지

### (1) DB가 Source of Truth, 외부 I/O는 "커밋 후 결과 반영"
- DB 커밋을 신호로 사용. 성공해야 Redis·Kafka·HTTP 반영.
- 실패하면 애초에 리스너가 안 불림 → 외부 상태가 오염되지 않음.

### (2) 리스너 내부 실패는 "로그만 + 다음으로"
- `handleTicketRefunded`의 3블록 독립 try-catch가 대표.
- 트레이드오프: 단순·빠른 복구 불필요 but **부분 실패를 모니터링이 잡아야 함**.

### (3) "rollback 경로는 AFTER_COMMIT을 못 쓴다"는 예외 규칙
- Stage 2의 `SelfPaymentService` 실패 분기가 직접 Kafka publish하는 이유가 여기.
- 트랜잭션이 롤백되므로 AFTER_COMMIT 훅이 fire 안 됨 → 이벤트로 감싸면 유실.

---

## ★ 핵심 질문

1. ★ `handleTicketingStarted`의 TTL 계산이 **이미 지난 시점의 스케줄**에서 어떻게 동작하는가? (음수 Duration) Redis 키는 어떻게 되는가?
2. ★ `handleTicketRefunded`에서 블록 1(refundCookie)만 실패한 경우 시스템 상태를 4가지 외부 상태(DB·Redis·HTTP·Kafka)로 표현하라.
3. ★ 모든 `@TransactionalEventListener(AFTER_COMMIT)`를 `@EventListener`로 바꾸면 생기는 버그를 **서로 다른 핸들러에서 3개** 나열하라.
4. `CartUpdatedEvent`는 왜 `added: boolean` 하나만으로 INCR/DECR을 구분하는가? 두 이벤트로 분리하면 장단점은?
5. `handleTicketRefunded`의 block 2에서 `if (cachePort.exists(stockKey))` 체크는 왜 필요한가? (힌트: TICKETING 이전에 환불된 Case A 티켓)

---

## 체크리스트

- [ ] `@TransactionalEventListener(AFTER_COMMIT)` vs `@EventListener`의 차이를 **DB 롤백 시 동작**으로 설명할 수 있다
- [ ] `handleTicketRefunded`의 3중 try-catch 각 블록 역할과 **왜 독립적인지** 설명할 수 있다
- [ ] `handleTicketingStarted`의 Redis 5키 + TTL 계산식(`startTime - 10min`)을 외웠다
- [ ] `ScheduleEventListener.handleScheduleInitialized`가 Quartz 5 Job을 AFTER_COMMIT에 등록하는 이유를 안다
- [ ] rollback 경로에서는 왜 AFTER_COMMIT 이벤트를 못 쓰는지 (Stage 2 SelfPaymentService 실패 분기와 연결) 설명할 수 있다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/infrastructure/event/TicketEventListener.java`
- `src/main/java/com/example/ticketservice/infrastructure/event/ScheduleEventListener.java`
- `src/main/java/com/example/ticketservice/application/event/*.java` (records 6종)
- `src/main/java/com/example/ticketservice/infrastructure/messaging/dto/event/*.java` (Kafka payload DTO)
- `docs/reference/infra/04-transactional-event-listener-pattern.md` — 공식 패턴 설명

← 이전: [Stage 3 — 출력 포트 & 어댑터](stage-03-ports-adapters.md)
→ 다음: [Stage 5 — Quartz Job + Kafka Consumer](stage-05-quartz-kafka.md)