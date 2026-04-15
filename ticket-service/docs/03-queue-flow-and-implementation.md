# 대기열 흐름 및 구현

## 개요

`ticketingTime`부터 `startTime - 10분`까지 유효한 선착순 대기열.
Redis atomic DECR로 재고를 선점하고, ZSet으로 순서를 유지한다.
재고 복구(환불·결제 실패) 시 `@Async`로 자동 드레인한다.

---

## 진입 흐름 (`QueueService.enter`)

> `QueueService.enter()`는 `@Transactional` 없음.
> 내부 DB 쿼리는 각자 별도 연결. 실제 트랜잭션은 `QueuePurchaseProcessor`가 관리.

```
POST /api/queue/{scheduleId}/enter
  │
  ▼
schedule.status == TICKETING 검증
  │
  ▼
Redis DECR stock:schedule:{scheduleId}
  │
  ├─ stockAfterDecr >= 0  →  [즉시 구매 시도]
  │     │
  │     ▼
  │  QueuePurchaseProcessor.tryPurchase()
  │     ├─ 성공: QueueEntryResponse(type=PURCHASED, ticket=...)
  │     └─ 실패(쿠키 부족): INSUFFICIENT_BALANCE 에러 반환
  │         (stock은 tryPurchase 내부에서 INCR 복구됨)
  │
  └─ stockAfterDecr < 0  →  [재고 없음]
        │
        └─ Redis INCR 복구 (원복)
        │
        ▼
     countByScheduleIdAndStatus(RESERVED) > 0?
        ├─ false → SOLD_OUT 에러
        └─ true  → [대기열 진입]
              │
              ▼
           ZSet rank 조회 → 이미 있으면 ALREADY_IN_QUEUE 에러
              │
              ▼
           ZADD queue:schedule:{scheduleId} {timestamp} {userId}
              │
              ▼
           TTL 설정 (startTime - 10min)
              │
              ▼
           QueueEntryResponse(type=QUEUED, position=rank+1)
```

---

## 개별 구매 처리 (`QueuePurchaseProcessor.tryPurchase`)

별도 `@Component` Bean — `@Async`/`@Transactional` 자기호출(self-invocation) 문제 방지.

```java
@Transactional
public Optional<TicketResponse> tryPurchase(Long scheduleId, UUID userId, int ticketNum)
```

```
1. Schedule 조회
2. Ticket.createReserved(schedule, ticketNum, userId)
3. ticketRepository.save(ticket)  ← ID 확보

4. UserPort.deductTicketFee(ticketId, cookie, userId)
   → HTTP POST /internal/users/deduct/cookie

5. flag=false (쿠키 부족):
   - cachePort.increment(stock)  ← Redis는 트랜잭션 밖, 즉시 복구
   - TransactionAspectSupport.setRollbackOnly()  ← ticket save 롤백
   - return Optional.empty()

6. flag=true (성공):
   - ticket.pay() → RESERVED → CONFIRMED
   - publishEvent(TicketPaidEvent)  ← AFTER_COMMIT에서 Kafka 발행
   - return Optional.of(TicketResponse)
```

**주의:** `setRollbackOnly()`는 현재 트랜잭션만 롤백. 호출자(drainQueue)에게는 영향 없음.

---

## 자동 드레인 (`QueueAutoProcessService`)

재고가 복구될 때마다 `@Async`로 호출되어 대기열을 소진한다.

### 트리거 시점

| 이벤트 | 위치 |
|--------|------|
| 환불 완료 후 stock INCR | `TicketEventListener.handleTicketRefunded` (AFTER_COMMIT) |
| 자율결제 실패 시 stock INCR | `SelfPaymentService.pay()` |
| 결제 완료 후 종료 조건 체크 | `TicketEventListener.handleTicketPaid` (AFTER_COMMIT) |

### drainQueue 루프 (윈도우 병렬 처리)

재고 수만큼 한 번에 수집 후 `CompletableFuture`로 병렬 처리한다.
stock=5이면 5개 HTTP 호출이 동시 실행 → 순차 대비 ~1/N 시간으로 단축.

```
while (true):
  stock    = GET(stock)
  queueSize = ZCARD(queue)

  if stock <= 0 OR queueSize == 0:
    break

  window = min(stock, queueSize)

  // 1단계: collectWindow — DECR + ZPOPMIN 순차 수집 (원자성 유지)
  tasks = []
  for i in 0..window:
    stockAfterDecr = DECR(stock)
    if stockAfterDecr < 0:
      INCR(stock); break
    userId = ZPOPMIN(queue)
    if userId == null:
      INCR(stock); break
    tasks.add(userId, computeTicketNum(stockAfterDecr))

  if tasks.empty: break

  // 2단계: processWindowParallel — 병렬 실행 후 완료 대기
  CompletableFuture.allOf(
    tasks.map(task ->
      runAsync(() -> tryPurchase(task))  // ForkJoinPool.commonPool()
      // 실패 → tryPurchase 내부에서 stock INCR 복구
      // 예외 → catch 후 stock INCR 복구
    )
  ).join()

  // 실패한 유저만큼 stock이 복구됨 → 다음 루프에서 재계산

// 루프 종료 후
checkTermination(scheduleId)
```

**스레드 분리:** `@Async` 스레드가 `.join()`으로 대기하고, 실제 tryPurchase는 `ForkJoinPool.commonPool()`에서 실행 → 데드락 없음.

### 종료 조건 체크 (`checkTermination`)

```
stock <= 0
AND countByStatus(RESERVED) == 0   ← 결제 대기 없음
AND queueSize > 0                   ← 남은 대기자 있음
  →  terminateQueue()
```

### 대기열 강제 종료 (`terminateQueue`)

Redis DEL의 반환값을 활용해 중복 발행 방지:

```java
boolean deleted = cachePort.delete(QUEUE_KEY_PREFIX + scheduleId);
if (!deleted) return;  // 다른 스레드가 이미 처리함

// 첫 번째 스레드만 Kafka 발행
eventPublisherPort.publish("queue.terminated", ..., new QueueTerminatedMessage(scheduleId));
```

---

## ticketNum 계산

```java
// schedule.seats = 전체 좌석 수 (예: 100)
// stockAfterDecr = DECR 후 값 (예: 42 → 43번째 티켓)
ticketNum = schedule.seats - stockAfterDecr;
// = 100 - 42 = 58번째 티켓
```

Case A에서 이미 생성된 CONFIRMED 티켓 수만큼 stock이 낮게 초기화되므로
ticketNum이 중복되지 않는다.

---

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/queue/{scheduleId}/enter` | 재고 있으면 즉시 구매, 없으면 대기열 진입 |
| `GET` | `/api/queue/{scheduleId}/position` | 대기열 순번 조회 (0이면 대기 아님) |

### 응답 타입

```json
// 즉시 구매 성공
{ "type": "PURCHASED", "ticket": { ... } }

// 대기열 진입
{ "type": "QUEUED", "scheduleId": 1, "position": 7 }
```

---

## 에러 코드

| 에러 | 상황 |
|------|------|
| `QUEUE_NOT_OPEN` | schedule.status != TICKETING |
| `INSUFFICIENT_BALANCE` | 재고 있는데 쿠키 부족 |
| `ALREADY_IN_QUEUE` | 이미 대기열에 있음 |
| `SOLD_OUT` | stock=0 AND RESERVED=0 (매진 확정) |