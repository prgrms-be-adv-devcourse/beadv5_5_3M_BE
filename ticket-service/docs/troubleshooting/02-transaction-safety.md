# 트랜잭션·데이터 정합성 (TX-001 ~ TX-007)

> `@Transactional` 내 부수효과, AFTER_COMMIT 패턴, 롤백 보상 관련 이슈와 해결 방법

---

## 핵심 원칙

`@Transactional` 메서드 내에서 DB 외 부수효과(Redis, HTTP, Kafka)를 실행하면 DB 롤백 시 부수효과만 남는 불일치가 발생한다. 해결 패턴:

1. **AFTER_COMMIT**: `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 부수효과 실행 → DB 커밋 확정 후에만 실행
2. **롤백 보상**: DB 커밋 실패 시 `TransactionSynchronization.afterCompletion(ROLLED_BACK)`에서 보상 HTTP 호출
3. **독립 try-catch**: AFTER_COMMIT 핸들러 내 독립적 부수효과는 각각 try-catch로 분리

---

## TX-001: TicketingStartService Redis 키 잔류 [CRITICAL → RESOLVED]

**파일:** `application/service/TicketingStartService.java`, `infrastructure/event/TicketEventListener.java`

### 현상

```
TicketingStartService.execute():
  cachePort.setCounter(stock/paying/seats/cookie/startTime)  ← Redis 성공
  scheduleRepository.save(schedule)                           ← DB 실패 → 롤백
결과: DB=IN_PROGRESSING, Redis=stock 키 존재 → 대기열 오픈 상태 💥
```

### 해결 — Redis 설정을 AFTER_COMMIT으로 이동

`TicketingStartedEvent`에 `remaining, seats, cookie, startTime` 필드를 추가. DB 커밋 확정 후 `TicketEventListener.handleTicketingStarted()`에서 Redis 5개 키 설정.

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleTicketingStarted(TicketingStartedEvent event) {
    Duration ttl = Duration.between(LocalDateTime.now(), event.startTime().minusMinutes(10));
    cachePort.setCounter("stock:schedule:" + event.scheduleId(), event.remaining(), ttl);
    cachePort.setCounter("paying:schedule:" + event.scheduleId(), 0, ttl);
    cachePort.setCounter("seats:schedule:" + event.scheduleId(), event.seats(), ttl);
    cachePort.setCounter("cookie:schedule:" + event.scheduleId(), event.cookie(), ttl);
    cachePort.set("startTime:schedule:" + event.scheduleId(), event.startTime().toString(), ttl);
}
```

| 시나리오 | 변경 전 | 변경 후 |
|---------|---------|---------|
| Redis 성공, DB 실패 | Redis 열림 💥 | Redis 미설정 (안전) |
| DB 성공, Redis 실패 | — | 구매 불가 (안전) |

---

## TX-002: RefundService 쿠키 이중 환불 [CRITICAL → RESOLVED]

**파일:** `application/service/RefundService.java`, `infrastructure/event/TicketEventListener.java`

### 현상

```
refund() 트랜잭션:
  userPort.refundCookie()     ← HTTP 성공 (쿠키 +N)
  ticketRepository.delete()   ← DB 예외 → 롤백
결과: 쿠키 환불됨 + 티켓 CONFIRMED 잔존 → 재환불 가능
```

### 해결 — HTTP 호출을 AFTER_COMMIT으로 이동

`RefundService`에서 `userPort` 의존성 제거. `TicketRefundedEvent` 발행 후 DB 커밋 확정된 뒤 `TicketEventListener.handleTicketRefunded()`에서 쿠키 환불 실행.

```java
// RefundService.refund()
ticketRepository.delete(ticket);
eventPublisher.publishEvent(new TicketRefundedEvent(ticketId, scheduleId, userId, cookie));
// HTTP 호출 없음 → DB 롤백 시 이벤트 미발행 → 쿠키 미환불 (안전)
```

---

## TX-003: SelfPaymentService 잔여 동기 checkAndProcess [MEDIUM → RESOLVED]

**파일:** `application/service/SelfPaymentService.java`

### 현상

Kafka 전환(RES-004) 후 `SelfPaymentService.pay()`에 `queueAutoProcessService.checkAndProcess()` 직접 호출이 남아 있음. `@Async` 제거 후 동기 실행 → 잔액 부족 경로에서 전체 드레인 동기 실행 → DB 커넥션 장시간 점유.

### 해결

`QueueAutoProcessService` 의존성 제거, Kafka `queue.drain` 직접 발행으로 교체.

```java
// rollback 경로: AFTER_COMMIT 이벤트 사용 불가 → Kafka 직접 발행
eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, schedule.getId().toString(),
        new QueueDrainMessage(schedule.getId()));
```

---

## TX-004: CartService Redis/DB 카운터 불일치 [MEDIUM → RESOLVED]

**파일:** `application/service/CartService.java`, `infrastructure/event/TicketEventListener.java`

### 현상

```java
cartRepository.save(cart);           // DB
cachePort.increment(CART_COUNT_KEY); // Redis
// save 후 commit 전 예외 → DB 롤백, Redis는 이미 증가
```

### 해결 — CartUpdatedEvent + AFTER_COMMIT

```java
// CartService
cartRepository.save(Cart.of(userId, scheduleId));
eventPublisher.publishEvent(new CartUpdatedEvent(scheduleId, userId, true));

// TicketEventListener (AFTER_COMMIT)
if (event.added()) cachePort.increment("cart:count:schedule:" + event.scheduleId());
else               cachePort.decrement("cart:count:schedule:" + event.scheduleId());
```

---

## TX-005: @Transactional 내 HTTP 쿠키 차감 — 롤백 보상 [CRITICAL → RESOLVED]

**파일:** `application/service/QueuePurchaseProcessor.java`, `application/service/SelfPaymentService.java`

### 현상

```java
@Transactional
public Optional<TicketResponse> tryPurchase(...) {
    ticketRepository.save(ticket);
    userPort.deductTicketFee(...);  // HTTP 성공
    ticket.pay();
    // DB 커밋 실패 시 → 쿠키 차감됨, 티켓 없음
}
```

### 해결 — CookieCompensationHelper 롤백 보상

HTTP 성공 후 `CookieCompensationHelper.registerRollbackRefund()`를 호출하여 `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` 콜백을 등록. DB 롤백 시 자동으로 쿠키 환불 HTTP 호출.

```java
// CookieCompensationHelper.registerRollbackRefund()
CookieCompensationHelper.registerRollbackRefund(userPort, ticket.getId(), schedule.getCookie(), userId);
```

내부적으로 `TransactionSynchronizationManager.registerSynchronization()`을 사용:

```java
public static void registerRollbackRefund(UserPort userPort, Long ticketId, int cookie, UUID userId) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
                try {
                    userPort.refundCookie(new RefundCookieRequest(ticketId, cookie, userId));
                } catch (Exception e) {
                    log.error("DB 롤백 쿠키 보상 실패 - ticketId={}, 수동 처리 필요", ticketId, e);
                }
            }
        }
    });
}
```

`QueuePurchaseProcessor`, `SelfPaymentService` 두 파일 모두 `CookieCompensationHelper.registerRollbackRefund()` 호출로 통일.

---

## TX-006: handleTicketRefunded 독립 try-catch [HIGH → RESOLVED]

**파일:** `infrastructure/event/TicketEventListener.java`

### 현상

```java
public void handleTicketRefunded(TicketRefundedEvent event) {
    userPort.refundCookie(...);            // 실패 시 예외
    cachePort.increment(stockKey);         // 스킵됨
    eventPublisherPort.publish(...);       // 스킵됨
}
```

HTTP 환불 실패 → 나머지 작업(stock 복구, Kafka 발행)도 전부 스킵 → 대기자 처리 지연.

### 해결

HTTP 환불, stock 복구, Kafka 발행은 서로 독립된 부수효과. 하나의 실패가 나머지를 차단하면 안 된다.

```java
public void handleTicketRefunded(TicketRefundedEvent event) {
    try { userPort.refundCookie(...); }
    catch (Exception e) { log.error("쿠키 환불 실패 - 수동 처리 필요", e); }

    try { cachePort.increment(stockKey); eventPublisherPort.publish(QUEUE_DRAIN, ...); }
    catch (Exception e) { log.error("stock 복구 실패", e); }

    try { eventPublisherPort.publish(TICKET_REFUNDED, ...); }
    catch (Exception e) { log.error("ticket.refunded 발행 실패", e); }
}
```

---

## TX-007: SelfPaymentService 직접 Kafka 발행 [MEDIUM → WONTFIX]

**파일:** `application/service/SelfPaymentService.java`

### 현상

잔액 부족 시 `throw` 전에 `eventPublisherPort.publish(QUEUE_DRAIN, ...)` 직접 호출. AFTER_COMMIT 패턴 미사용.

### 판단: 의도된 설계

- 이 경로에서 예외를 throw → 트랜잭션 롤백 → AFTER_COMMIT 이벤트 발동 불가 → 직접 발행이 유일한 수단
- DB 변경사항 없음 (save 미호출). 롤백되는 것은 빈 트랜잭션 → 실질적 불일치 없음
- `queue.drain` 유실 시 다음 결제/환불 이벤트에서 재트리거