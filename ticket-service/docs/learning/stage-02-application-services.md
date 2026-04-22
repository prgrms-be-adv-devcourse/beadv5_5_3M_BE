# Stage 2 — 애플리케이션 서비스 (유스케이스)

> **목표**: "사용자 요청 → Service → 엔티티 메서드 호출 → 이벤트 발행"까지의 실제 코드 흐름을 9개 서비스로 훑는다.
> 이 Stage가 본 가이드의 **척추**. 여기서 잡은 흐름이 Stage 3~7 전부의 전제가 된다.
> **예상 소요**: 2~3일

---

## 0. 서비스 9종 한눈에

| # | 서비스 | 트리거 | 핵심 동작 | 이벤트 발행 |
|---|--------|-------|-----------|-------------|
| 1 | `CartService` | HTTP (사용자) | 카트 담기/빼기/조회 | `CartUpdatedEvent` |
| 2 | `CartCloseService` | Quartz (T-24h) | Case A/B 분기 + RESERVED bulk 생성 | `CartClosedEvent` |
| 3 | `TicketingStartService` | Quartz (`ticketingTime`) | 미결제 정리 + TICKETING 전이 | `TicketingStartedEvent` |
| 4 | `SelfPaymentService` | HTTP (Case A 결제) | ★ HTTP → pay() → 보상등록 | `TicketPaidEvent` |
| 5 | `QueuePurchaseProcessor` | 내부 (드레인 워커가 호출) | 대기열 유저 1명 단위 구매 처리 | `TicketPaidEvent` |
| 6 | `QueueAutoProcessService` | Kafka `queue.drain` | ★★ 윈도우 드레인·병렬 처리·종료 판정 | `QueueTerminatedMessage` |
| 7 | `RefundService` | HTTP (사용자) | CONFIRMED 삭제 | `TicketRefundedEvent` |
| 8 | `CookieCompensationHelper` | Service 내부에서 호출 | ★ DB 롤백 시 쿠키 환불 훅 등록 | — |
| 9 | `ProvideTicketFeeService` | Quartz (01:00) | Spring Batch Job 트리거 | — (Batch Writer가 발행) |

---

## 1. CartService — 카트 담기/빼기

`application/service/CartService.java:35-60`

```java
@Transactional
@Override
public void addToCart(UUID userId, Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    if (schedule.getStatus() != ScheduleStatus.CART) {
        throw ScheduleErrorCode.NOT_IN_CART_PERIOD.of(scheduleId);
    }

    if (cartRepository.existsByUserIdAndScheduleId(userId, scheduleId)) {
        throw TicketErrorCode.ALREADY_IN_CART.of(scheduleId);
    }

    cartRepository.save(Cart.of(userId, scheduleId));
    // Redis 카운터 조작은 AFTER_COMMIT으로 위임 — DB 롤백 시 Redis 불일치 방지
    eventPublisher.publishEvent(new CartUpdatedEvent(scheduleId, userId, true));
}
```

**주목할 점**:
- Redis INCR을 **여기서 직접 부르지 않는다**. `CartUpdatedEvent` 발행 → AFTER_COMMIT 리스너가 INCR.
  - 만약 여기서 `cachePort.increment(...)`를 했다면? DB 커밋 실패 시 Redis는 이미 INCR되어 **수요 카운터 stale**.
- 가드 3종: Schedule 존재·CART 단계·중복 담기 금지. **엔티티 invariant 전에 서비스 invariant**.

`getCartCount()`는 `CART_COUNT` Redis 키를 바로 읽는다 — 대량 조회라 DB로 가지 않는다.

---

## 2. CartCloseService — Case A vs Case B 분기

`application/service/CartCloseService.java:32-66`

```java
@Transactional
@Override
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    List<Cart> carts = cartRepository.findAllByScheduleId(scheduleId);
    int demand = carts.size();
    int seats = schedule.getSeats();

    List<UUID> userIds = carts.stream().map(Cart::getUserId).toList();

    String caseType;
    if (demand < seats) {
        // Case A: 수요 < 재고 → 장바구니 유저 전원 가예약
        caseType = "CASE_A";
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < carts.size(); i++) {
            tickets.add(Ticket.createReserved(schedule, i + 1, carts.get(i).getUserId()));
        }
        ticketRepository.saveAll(tickets);
    } else {
        // Case B: 수요 >= 재고 → 선착순 대기열 모드
        caseType = "CASE_B";
    }

    schedule.closeCart(); // CART → IN_PROGRESSING
    scheduleRepository.save(schedule);

    cartRepository.deleteAllByScheduleId(scheduleId);

    eventPublisher.publishEvent(new CartClosedEvent(scheduleId, caseType, schedule.getSeats(), userIds));
}
```

**주목할 점**:
- 분기 조건은 **`demand < seats`**. 같으면 Case B (정확히 찼을 때도 선착순 — 이유: "티켓팅 참여권"의 의미).
- Case A에서는 `ticketNum = i + 1` 로 **Cart 순서대로 좌석 번호 할당**. 즉 먼저 담은 사람이 1번.
- `cartRepository.deleteAllByScheduleId(scheduleId)` — 마감 후 Cart 테이블 전부 삭제. 이후 조회 불가.
- `CartClosedEvent.userIds`를 담는 이유: Case B일 때 대기열 시드에 사용 (AFTER_COMMIT 리스너에서).

### Case A · Case B 타임라인

```
Case A: demand < seats 예) 좌석 100, 카트 70
  ├ CartClose: 70명 전원 RESERVED 생성
  ├ 24h 자율결제 창 → SelfPaymentService
  └ TicketingStart: 미결제 RESERVED 삭제 → 결제 안 한 사람의 좌석은 티켓팅 풀로 환원

Case B: demand >= seats 예) 좌석 100, 카트 500
  ├ CartClose: 티켓 0개 생성
  ├ 24h 무대기 → TicketingStart 시점에 선착순 경쟁
  └ TicketingStart: 재고 100 시딩 → 사용자가 queue:enter → QueueDrainConsumer 트리거
```

---

## 3. TicketingStartService — 미결제 정리 + TICKETING 전이

`application/service/TicketingStartService.java:27-54`

```java
@Transactional
@Override
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    // 1. 미결제 RESERVED 티켓 일괄 삭제 — 실패 시 TICKETING 전이 중단
    try {
        ticketCleanupBatchPort.run(scheduleId);
    } catch (Exception e) {
        throw new RuntimeException("미결제 RESERVED 티켓 삭제 실패 - scheduleId=" + scheduleId
                + ", TICKETING 전이를 중단합니다.", e);
    }

    // 2. 남은 재고 계산: 총 좌석 - 결제 완료(CONFIRMED) 수
    long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED);
    long remaining = schedule.getSeats() - confirmedCount;

    // 3. IN_PROGRESSING → TICKETING
    schedule.startTicketing();
    scheduleRepository.save(schedule);

    // 4. DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Redis 키 설정 + Kafka 발행
    eventPublisher.publishEvent(new TicketingStartedEvent(
            scheduleId, remaining, schedule.getSeats(), schedule.getCookie(), schedule.getStartTime()));
}
```

**주목할 점**:
- **Batch 호출이 맨 먼저**. `TicketCleanupBatchAdapter`가 동기로 실행되어 완료될 때까지 Quartz 스레드가 block.
  - 실패 시 Schedule 상태 전이를 **하지 않는다** (`throw new RuntimeException`). 미결제 티켓 잔류 + TICKETING 시작이 겹치는 사태 방지.
- `remaining = seats - CONFIRMED count` → AFTER_COMMIT 리스너가 `stock:schedule:{id}`에 이 값을 심음.
- `Stage 1`의 "왜 `seats`·`cookie`에 set 메서드가 없는가" 에 대한 답이 여기에 — **이 시점 이후 DB 값은 SoT가 아니고 Redis가 SoT**.

---

## 4. SelfPaymentService — ★ HTTP → DB 순서의 정수

`application/service/SelfPaymentService.java:38-82`

```java
@Transactional
@Override
public TicketResponse pay(UUID userId, Long ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));

    if (!ticket.getUserId().equals(userId)) {
        throw TicketErrorCode.NOT_YOUR_TICKET.of(ticketId);
    }

    if (ticket.getStatus() != TicketStatus.RESERVED) {
        throw TicketErrorCode.NOT_RESERVED.of(ticketId);
    }

    Schedule schedule = ticket.getSchedule();

    // 쿠키 차감 먼저 — 실패 시 상태 전환 없이 예외 발생
    DeductCookieResponse response = userPort.deductTicketFee(
            new DeductCookieRequest(ticketId, schedule.getCookie(), userId));

    if (!response.flag()) {
        // RESERVED 상태이므로 stock 복구 → queue.drain 발행
        // rollback 경로이므로 AFTER_COMMIT 이벤트 사용 불가 → Kafka 직접 발행
        String stockKey = RedisKeys.STOCK + schedule.getId();
        if (cachePort.exists(stockKey)) {
            cachePort.increment(stockKey);
            eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, schedule.getId().toString(),
                    new QueueDrainMessage(schedule.getId()));
        }
        throw TicketErrorCode.INSUFFICIENT_BALANCE.of((long) schedule.getCookie());
    }

    // DB 커밋 실패 시 쿠키 차감 보상
    CookieCompensationHelper.registerRollbackRefund(userPort, ticketId, schedule.getCookie(), userId);

    // 결제 성공 후 상태 전환 RESERVED → CONFIRMED
    ticket.pay();
    ticketRepository.save(ticket);

    // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Kafka ticket.paid 발행
    eventPublisher.publishEvent(new TicketPaidEvent(ticketId, schedule.getId(), userId, schedule.getCookie()));
    return TicketResponse.from(ticket);
}
```

### ★ 질문: HTTP 차감이 왜 `ticket.pay()`보다 먼저?

| 순서 | 잔액 부족일 때 | DB 커밋 실패일 때 |
|------|----------------|---------------------|
| **HTTP → DB (현재)** | response.flag=false → 예외 → DB 변경 없음 ✅ | 보상 헬퍼가 afterCompletion에서 refund 호출 ✅ |
| DB → HTTP (가정) | ticket.pay() 먼저 → HTTP 실패 → DB 롤백 필요 → **상태 전환을 낭비** | 커밋 후 HTTP 실패하면 **CONFIRMED됐는데 쿠키 차감 안 됨** = 무료 티켓 🛑 |

### "Happy path = AFTER_COMMIT, Sad path = 직접 발행"

- **성공 분기** (line 78): `TicketPaidEvent`는 Spring 이벤트 → AFTER_COMMIT 리스너가 Kafka 발행.
- **실패 분기** (line 64-65): `eventPublisherPort.publish(...)` **Kafka 직접 발행**.
  - 이유: `throw`로 트랜잭션이 롤백되어 **AFTER_COMMIT은 fire되지 않음**. 이벤트로 감싸면 유실.

---

## 5. QueuePurchaseProcessor — 드레인 워커가 호출하는 1인 구매

`application/service/QueuePurchaseProcessor.java:41-67`

```java
@Transactional
public Optional<TicketResponse> tryPurchase(Long scheduleId, UUID userId, int ticketNum) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    Ticket ticket = Ticket.createReserved(schedule, ticketNum, userId);
    ticketRepository.save(ticket); // ID 확보를 위해 먼저 저장

    DeductCookieResponse response = userPort.deductTicketFee(
            new DeductCookieRequest(ticket.getId(), schedule.getCookie(), userId));

    if (!response.flag()) {
        cachePort.increment(RedisKeys.STOCK + scheduleId); // 재고 복구 (Redis는 트랜잭션 밖)
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // 티켓 save 롤백
        return Optional.empty();
    }

    // DB 커밋 실패 시 쿠키 차감 보상
    CookieCompensationHelper.registerRollbackRefund(userPort, ticket.getId(), schedule.getCookie(), userId);

    ticket.pay(); // RESERVED → CONFIRMED
    eventPublisher.publishEvent(new TicketPaidEvent(ticket.getId(), scheduleId, userId, schedule.getCookie()));
    return Optional.of(TicketResponse.from(ticket));
}
```

**SelfPaymentService와의 차이**:
- `SelfPayment`는 **RESERVED 티켓이 이미 존재** → 조회 후 `ticket.pay()`.
- `QueuePurchase`는 **티켓을 직접 생성** → save → HTTP → pay(). 티켓 없는 상태에서 시작.
- 실패 시 Redis `stock`을 복구하고 (이미 `QueueAutoProcessService.collectWindow`에서 DECR됐음), `setRollbackOnly()`로 티켓 save를 취소.

**주목할 점**:
- `ticketRepository.save(ticket)` **먼저 호출**하는 이유: HTTP 요청에 `ticket.getId()`가 필요 (user-service가 결제 ID로 사용).
- 실패 분기에서 `cachePort.increment(...)`는 **트랜잭션 밖에서 실행**. Redis는 @Transactional 영향을 받지 않음.

---

## 6. QueueAutoProcessService — ★★ 가장 복잡

Case B 티켓팅의 심장부. Kafka `queue.drain` 컨슈머가 이걸 호출.

### 6.1 진입점 — `checkAndProcess`

`application/service/QueueAutoProcessService.java:46-54`

```java
public void checkAndProcess(Long scheduleId) {
    Long queueSize = cachePort.getZSetSize(RedisKeys.QUEUE + scheduleId);
    if (queueSize == null || queueSize == 0) {
        return; // 대기열 비어있음
    }

    drainQueue(scheduleId);
    checkTermination(scheduleId);
}
```

### 6.2 드레인 루프 — `drainQueue`

`application/service/QueueAutoProcessService.java:58-78`

```java
private static final int MAX_DRAIN_ITERATIONS = 500;

private void drainQueue(Long scheduleId) {
    for (int i = 0; i < MAX_DRAIN_ITERATIONS; i++) {
        Long stock = cachePort.getCounter(RedisKeys.STOCK + scheduleId);
        Long queueSize = cachePort.getZSetSize(RedisKeys.QUEUE + scheduleId);

        if (stock == null || stock <= 0 || queueSize == null || queueSize == 0) {
            break;
        }

        int window = (int) Math.min(stock, queueSize);
        List<PurchaseTask> tasks = collectWindow(scheduleId, window);

        if (tasks.isEmpty()) {
            break;
        }

        processWindowParallel(scheduleId, tasks);
    }
}
```

**주목할 점**:
- **윈도우 크기** = `min(stock, queueSize)`. 재고와 대기자 중 작은 쪽.
- `MAX_DRAIN_ITERATIONS = 500` → 무한 루프 방지 안전망.
- 한 윈도우가 끝날 때까지 다음 윈도우로 넘어가지 않음 (`join()`으로 기다림).

### 6.3 ★ DECR-first — `collectWindow`

`application/service/QueueAutoProcessService.java:85-111`

```java
private List<PurchaseTask> collectWindow(Long scheduleId, int window) {
    List<PurchaseTask> tasks = new ArrayList<>(window);
    String stockKey = RedisKeys.STOCK + scheduleId;
    String queueKey = RedisKeys.QUEUE + scheduleId;
    String payingKey = RedisKeys.PAYING + scheduleId;

    for (int i = 0; i < window; i++) {
        Long stockAfterDecr = cachePort.decrement(stockKey);       // ① DECR 먼저
        if (stockAfterDecr == null || stockAfterDecr < 0) {
            if (stockAfterDecr != null) cachePort.increment(stockKey); // 음수 시 복원
            break;
        }
        String userIdStr = cachePort.popMinFromZSet(queueKey);      // ② 성공 시에만 ZPOPMIN
        if (userIdStr == null) {
            cachePort.increment(stockKey);                          // 큐 비었으면 stock 복원
            break;
        }
        try {
            tasks.add(new PurchaseTask(UUID.fromString(userIdStr), computeTicketNum(scheduleId, stockAfterDecr)));
            cachePort.increment(payingKey);                         // paying 증가
        } catch (IllegalArgumentException e) {
            cachePort.increment(stockKey);
        }
    }
    return tasks;
}
```

### ★ 왜 DECR을 ZPOPMIN보다 먼저 하는가?

| 순서 | stock=1, 대기자 5명일 때 동시 진입 |
|------|-------------------------------------|
| **DECR → ZPOPMIN (현재)** | 스레드 A: DECR→0(성공), B: DECR→-1(실패 즉시 롤백). A만 ZPOPMIN 진행. 대기열에서 **정확히 1명만 팝** ✅ |
| ZPOPMIN → DECR (가정) | A, B 둘 다 ZPOPMIN 성공 (각기 다른 사람 팝) → 이후 A: DECR→0, B: DECR→-1. B가 팝한 사용자는 큐에서 **사라졌는데 재고 없음** → 다시 큐에 넣어야 함 (복잡·에러 가능) 🛑 |

**원자 감소 = 진입 토큰 배부**. 재고 토큰이 없으면 애초에 팝을 시도조차 않는다.

### 6.4 병렬 실행 — `processWindowParallel`

`application/service/QueueAutoProcessService.java:118-134`

```java
private void processWindowParallel(Long scheduleId, List<PurchaseTask> tasks) {
    String stockKey = RedisKeys.STOCK + scheduleId;
    String payingKey = RedisKeys.PAYING + scheduleId;
    List<CompletableFuture<Void>> futures = tasks.stream()
            .map(task -> CompletableFuture.runAsync(() -> {
                try {
                    purchaseProcessor.tryPurchase(scheduleId, task.userId(), task.ticketNum());
                } catch (Exception e) {
                    log.error("tryPurchase 예외 - scheduleId={}, userId={}, stock 복구", scheduleId, task.userId(), e);
                    cachePort.increment(stockKey);
                } finally {
                    cachePort.decrement(payingKey);
                }
            }, queueExecutor))
            .toList();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}
```

**주목할 점**:
- `queueExecutor`: core=4, max=8, **CallerRunsPolicy** (Stage 5에서 깊이).
- `.join()` → 윈도우 전체 완료까지 Kafka 컨슈머 스레드 block → 소비 속도가 드레인 속도에 맞춰 자연 조절.
- `finally { paying DECR }` → 성공·실패 무관하게 `paying` 감소. 누수 방지.
  - 단, JVM 크래시 시 finally 미실행 → 영구 누수 (Stage 7 시나리오 E).

### 6.5 종료 판정 — `checkTermination`

`application/service/QueueAutoProcessService.java:138-161`

```java
private void checkTermination(Long scheduleId) {
    Long stockLeft = cachePort.getCounter(RedisKeys.STOCK + scheduleId);
    Long payingCount = cachePort.getCounter(RedisKeys.PAYING + scheduleId);
    Long queueSize = cachePort.getZSetSize(RedisKeys.QUEUE + scheduleId);

    boolean noStock = stockLeft == null || stockLeft <= 0;
    boolean noPaying = payingCount == null || payingCount <= 0;
    boolean queueHasWaiters = queueSize != null && queueSize > 0;

    if (noStock && noPaying && queueHasWaiters) {
        terminateQueue(scheduleId);
    }
}

private void terminateQueue(Long scheduleId) {
    // atomic DEL: 첫 번째 스레드만 true 반환 → 중복 Kafka 발행 방지
    boolean deleted = cachePort.delete(RedisKeys.QUEUE + scheduleId);
    if (!deleted) {
        return;
    }
    eventPublisherPort.publish(KafkaTopics.QUEUE_TERMINATED, scheduleId.toString(),
            new QueueTerminatedMessage(scheduleId));
}
```

**종료 조건 3개 AND**:
1. `stock <= 0` — 재고 없음
2. `paying <= 0` — 현재 구매 중인 사람도 없음 (환불될 가능성 제거)
3. `queue > 0` — 아직 대기자가 남아있음

→ 세 조건 모두 참이어야 **대기자에게 SOLD_OUT 선언**. `paying > 0`이면 환불이 올 수도 있으므로 종료 금지.

→ `delete(key)`의 반환값으로 **첫 번째 호출한 스레드만** Kafka 발행. 중복 발행 방지.

---

## 7. RefundService — 과감한 Service, 무거운 리스너

`application/service/RefundService.java:25-51`

```java
@Transactional
@Override
public void refund(UUID userId, Long ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));

    if (!ticket.getUserId().equals(userId)) {
        throw TicketErrorCode.NOT_YOUR_TICKET.of(ticketId);
    }

    if (ticket.getStatus() != TicketStatus.CONFIRMED) {
        throw TicketErrorCode.NOT_CONFIRMED.of(ticketId);
    }

    Long scheduleId = ticket.getSchedule().getId();
    Integer cookie = ticket.getSchedule().getCookie();

    // CONFIRMED 티켓 DELETE
    ticketRepository.delete(ticket);

    // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서:
    // - 쿠키 환불 HTTP 호출 (DB 커밋 후 실행 → 이중 환불 방지)
    // - stock INCR (티켓팅 중이면) + queue.drain 발행
    // - Kafka ticket.refunded 발행
    eventPublisher.publishEvent(new TicketRefundedEvent(ticketId, scheduleId, userId, cookie));
}
```

### ★ 왜 `refundCookie()` HTTP를 Service가 아닌 리스너에서?

**비교**: SelfPayment는 HTTP → DB, Refund는 DB → HTTP.

- **SelfPayment (HTTP 먼저)**: HTTP 실패 = 티켓팅 안 됨 (유저에게 즉시 응답). 성공 시에만 DB 변경.
- **Refund (DB 먼저)**: HTTP를 먼저 하면? → 쿠키 환불됨 + 티켓 삭제 실패하면 → **이중 환불**(티켓 살아있는데 쿠키 돌려받음).

HTTP `refund`는 idempotent 보장이 없으므로, **DB 커밋을 먼저** 하고 AFTER_COMMIT 리스너에서 HTTP 호출. 리스너 실패 시에는 쿠키 미환불이지만 티켓은 이미 삭제 → **수동 개입 대상** (운영 알람·보상 워커 필요).

---

## 8. CookieCompensationHelper — ★ 26줄의 보상 헬퍼

`application/service/CookieCompensationHelper.java` 전체:

```java
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookieCompensationHelper {

    public static void registerRollbackRefund(UserPort userPort, Long ticketId, int cookie, UUID userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        userPort.refundCookie(new RefundCookieRequest(ticketId, cookie, userId));
                        log.warn("DB 롤백 쿠키 보상 완료 - ticketId={}, userId={}", ticketId, userId);
                    } catch (Exception e) {
                        log.error("DB 롤백 쿠키 보상 실패 - ticketId={}, userId={}, cookie={}, 수동 처리 필요",
                                ticketId, userId, cookie, e);
                    }
                }
            }
        });
    }
}
```

### 동작 타임라인

```
[t0]  HTTP deductCookie 호출 → 유저의 쿠키 차감 완료
[t1]  registerRollbackRefund() 호출 → Spring TransactionSynchronization 훅 등록
[t2]  ticket.pay() 호출 → 상태 RESERVED → CONFIRMED
[t3]  트랜잭션 커밋 시도

갈림길:
  [t4-A] 커밋 성공 → afterCompletion(STATUS_COMMITTED) → IF 절 skip ✅ 아무 일 없음
  [t4-B] 커밋 실패 → afterCompletion(STATUS_ROLLED_BACK) → userPort.refundCookie() 호출 ✅ 쿠키 보상
```

### 핵심 포인트

- `TransactionSynchronization.afterCompletion(int status)`는 **Spring이 트랜잭션 종료 후 무조건 콜백**.
  - 3가지 status: `STATUS_COMMITTED`, `STATUS_ROLLED_BACK`, `STATUS_UNKNOWN`.
- `if (status == STATUS_ROLLED_BACK)` → 오직 롤백 시에만 환불.
- **보상 HTTP마저 실패하면?** → catch 블록에서 로그만. **실운영이라면 DLQ·수동 처리 알람 필수**.

### 이 헬퍼가 등록되지 않는 코드 경로는?

- `RefundService`: DB→HTTP 순서라 필요 없음. (DB 롤백되면 HTTP 호출 자체가 안 일어남.)
- `SelfPaymentService`의 잔액부족 분기 (line 58-68): 여기는 **HTTP 응답 `flag=false`만 받고 차감 안 됨** → 보상 필요 없음. 정상적으로 예외 던져 롤백.

---

## 9. ProvideTicketFeeService — Spring Batch 트리거

`application/service/ProvideTicketFeeService.java` 전체 (23줄):

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvideTicketFeeService implements ProvideTicketFeeUseCase {

    private final TicketProvideBatchPort ticketProvideBatchPort;

    @Override
    public void provide() {
        boolean success = ticketProvideBatchPort.run();
        if (!success) {
            log.error("일일 대금 지급 배치 실패");
        }
    }
}
```

**주목할 점**:
- 서비스가 **배치 Job 실행만 위임**. 실제 비즈니스 로직은 Spring Batch Reader/Processor/Writer에 (Stage 6).
- `TicketProvideBatchPort`는 포트 인터페이스. 구현은 `TicketProvideBatchAdapter`가 JobOperator로 Job 실행.

---

## 10. 이벤트와 Service의 분업표

| 행위 | Service 책임 | 이벤트 리스너 책임 |
|------|----------------|---------------------|
| 카트 담기/빼기 | DB row 저장·삭제 | Redis 카운터 INCR/DECR |
| 카트 마감 | 티켓 bulk 생성·Cart 삭제·상태 전이 | `cart.closed` Kafka 발행 |
| 티켓팅 시작 | Batch 호출·상태 전이 | Redis 5개 키 세팅·`ticketing.started` Kafka |
| 자율결제 성공 | HTTP 차감·ticket.pay() | `ticket.paid` Kafka + queue.drain |
| 자율결제 실패 | 직접 Kafka `queue.drain` 발행 | — (리스너 우회) |
| 환불 | ticket.delete() | HTTP 환불·stock 복구·`ticket.refunded` Kafka |
| 대기열 구매 성공 | createReserved→save→HTTP→pay | `ticket.paid` Kafka + queue.drain |
| 대기열 구매 실패 | 직접 Redis stock 복구·`setRollbackOnly()` | — |
| 대기열 종료 | atomic DEL queue | — (Service가 직접 Kafka 발행) |

→ 읽어야 할 원칙: **DB 변경은 Service, 외부 I/O는 리스너**. 단, **rollback 경로는 예외** (Service가 직접 발행).

---

## 11. 설계 교훈 3가지

### (1) HTTP→DB 순서는 "무엇이 실패해도 안전한가"로 결정
- 쿠키 차감 먼저 → 실패 시 DB 변경 없음 (단순 throw).
- 티켓 삭제 먼저 → 성공 시 쿠키 환불 (보상 훅 불필요, 단 HTTP 실패는 수동 복구).

### (2) Rollback 경로는 AFTER_COMMIT을 쓸 수 없다
- 트랜잭션이 커밋되지 않으니 리스너가 fire되지 않음.
- 해결: Service가 직접 Kafka publish (`SelfPaymentService` line 64).

### (3) 동시성은 "원자 연산으로 토큰 배분"으로 환원
- `DECR stock`이 토큰 — 있으면 다음 단계, 없으면 중단.
- ZPOPMIN은 토큰 획득 후에만 시도 → 큐 소모 없이 빠르게 빠짐.

---

## ★ 핵심 질문

1. ★ `SelfPaymentService.pay()`에서 HTTP 차감을 **ticket.pay() 뒤로 이동**하면 어떤 버그가 가능한가? 구체적 타임라인으로.
2. ★ `CookieCompensationHelper.registerRollbackRefund`는 **커밋 성공 시에도 실행되는가**? 코드로 답하라.
3. ★ `QueueAutoProcessService.collectWindow`에서 **DECR을 ZPOPMIN 뒤로 이동**하면 어떤 race가 생기는가?
4. `RefundService`는 왜 `CookieCompensationHelper`를 쓰지 않는가? (힌트: HTTP 호출 시점)
5. `processWindowParallel`의 `finally { paying DECR }`이 실행 보장되지 않는 경우는? (힌트: JVM 종료)
6. `CartCloseService`에서 `demand == seats`일 때 Case A vs Case B 중 어느 쪽인가? 코드로 확인하라.

---

## 체크리스트

- [ ] `CartService.addToCart` → `CartCloseService` → `TicketingStartService`로 이어지는 Schedule 상태 전이 3단계를 코드 라인으로 짚을 수 있다
- [ ] `SelfPaymentService`의 HTTP→DB 순서 이유를 "잔액부족"과 "DB 롤백" 두 실패 케이스로 설명할 수 있다
- [ ] `CookieCompensationHelper.afterCompletion`이 `STATUS_ROLLED_BACK`일 때만 실행됨을 코드로 확인했다
- [ ] `QueueAutoProcessService.collectWindow`의 DECR-first 패턴을 "토큰 배부" 비유로 설명할 수 있다
- [ ] `checkTermination`의 3개 AND 조건(stock, paying, queue)을 암기했다
- [ ] `RefundService`는 DB→HTTP 순서, `SelfPaymentService`는 HTTP→DB 순서인 이유를 구분해 말할 수 있다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/application/service/*.java` — 위 9개 서비스 전체
- `src/main/java/com/example/ticketservice/application/event/*.java` — 이벤트 records (Stage 4에서 상세)
- `docs/reference/flow/02-cart-reservation-flow.md` — CartClose 흐름
- `docs/reference/flow/03-queue-flow-and-implementation.md` — 대기열 상세
- `docs/reference/flow/04-queue-drain-strategy.md` — DECR-first 공식 설명
- `docs/troubleshooting/02-transaction-safety.md` — TX-001~007 버그 사례 (Stage 8에서 활용)

← 이전: [Stage 1 — 도메인 모델](stage-01-domain.md)
→ 다음: [Stage 3 — 출력 포트 & 어댑터](stage-03-ports-adapters.md)