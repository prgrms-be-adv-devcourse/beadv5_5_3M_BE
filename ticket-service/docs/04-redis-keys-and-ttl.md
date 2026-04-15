# Redis 키 목록, 용도, 만료 시간

## 전체 요약

| 키 패턴 | 타입 | TTL | 설정 위치 |
|---------|------|-----|----------|
| `cart:count:schedule:{id}` | String (Counter) | `ticketingTime - 24h - now` | ScheduleEventListener |
| `stock:schedule:{id}` | String (Counter) | `startTime - 10min - now` | TicketingStartService |
| `paying:schedule:{id}` | String (Counter) | `startTime - 10min - now` | TicketingStartService |
| `queue:schedule:{id}` | ZSet | `startTime - 10min - now` | QueueService.enter() |

---

## 1. 장바구니 수요 카운터

```
key:   cart:count:schedule:{scheduleId}
type:  String (정수 인코딩)
value: 장바구니에 담은 유저 수
```

### 생명주기

```
[설정] ScheduleEventListener.handleScheduleInitialized (AFTER_COMMIT)
  → cachePort.setCounter("cart:count:schedule:{id}", 0, ttl)
  → TTL = ticketingTime - 24h - now  ← 장바구니 마감 시점에 만료

[INCR] CartService.addToCart()
[DECR] CartService.removeFromCart()
[GET]  CartService.getCartCount()  ← DB COUNT 쿼리 대신 사용

[소멸] TTL 만료 (마감 시점) 또는 CartCloseService에서 명시적 삭제 없음
      (TTL이 도달하면 자동 삭제)
```

### 용도

CartCloseService가 `cartCount vs schedule.seats`를 비교할 때 사용.
DB COUNT 쿼리 없이 O(1)로 수요 파악.

---

## 2. 재고 카운터

```
key:   stock:schedule:{scheduleId}
type:  String (정수 인코딩)
value: 현재 구매 가능한 잔여 좌석 수
```

### 생명주기

```
[설정] TicketingStartService.execute()
  → remaining = seats - count(CONFIRMED 티켓)
  → cachePort.setCounter("stock:schedule:{id}", remaining, ttl)
  → TTL = startTime - 10min - now  ← 공연 10분 전 티켓팅 마감

[DECR] QueueService.enter()  ← 구매 시도 시 선점
       QueueAutoProcessService.collectWindow()  ← 윈도우 수집 루프

[INCR] QueuePurchaseProcessor.tryPurchase() (쿠키 부족 시 복구)
       QueueAutoProcessService.collectWindow() (재고 없음·대기열 비어있음 시 복구)
       QueueAutoProcessService.processWindowParallel() (tryPurchase 예외 시 복구)
       SelfPaymentService.pay() (쿠키 부족 시 복구)
       TicketEventListener.handleTicketRefunded() (환불 시 복구)

[소멸] TTL 만료 (startTime - 10min)
```

### 값의 의미

```
stock > 0   →  구매 가능
stock == 0  →  재고 없음, 대기열 진입 가능 여부는 RESERVED 수 확인 필요
stock < 0   →  과점 (DECR 후 즉시 INCR로 복구)
```

### 키 존재 여부의 의미

```
EXISTS stock:schedule:{id} == true   →  티켓팅 진행 중
EXISTS stock:schedule:{id} == false  →  티켓팅 전 또는 마감됨
```

`TicketEventListener.handleTicketRefunded`에서 EXISTS 체크 후 INCR 여부 결정:
```java
if (cachePort.exists(stockKey)) {
    cachePort.increment(stockKey);
    queueAutoProcessService.checkAndProcess(scheduleId);
}
```

---

## 3. 결제 진행 카운터

```
key:   paying:schedule:{scheduleId}
type:  String (정수 인코딩)
value: 현재 tryPurchase 호출 중인 유저 수
```

### 생명주기

```
[설정] TicketingStartService.execute()
  → cachePort.setCounter("paying:schedule:{id}", 0, ttl)
  → TTL = startTime - 10min - now  ← stock 키와 동일

[INCR] QueueService.enter()          ← tryPurchase 시작 전
       QueueAutoProcessService.collectWindow()  ← 수집 성공 시

[DECR] QueueService.enter()  (finally)  ← tryPurchase 완료 후 (성공/실패/예외 모든 경로)
       QueueAutoProcessService.processWindowParallel() (finally)

[소멸] TTL 만료 (startTime - 10min)
```

### 용도

stock=0일 때 대기열 진입 가능 여부를 판단한다.
`paying > 0`이면 현재 결제 시도 중인 유저가 있고, 실패 시 stock이 복구될 수 있어 대기열 진입을 허용한다.
`paying == 0`이면 stock 복구 가능성이 없으므로 SOLD_OUT으로 판단한다.

```
stock > 0  →  즉시 구매 시도 (paying 무관)
stock == 0, paying > 0  →  대기열 진입 허용
stock == 0, paying == 0  →  SOLD_OUT
```

DB `COUNT(RESERVED)` 쿼리를 대체해 O(1)로 판단 가능.

### 왜 DB COUNT 대신 Redis paying 카운터인가

| 항목 | DB COUNT | Redis paying |
|------|----------|--------------|
| 정확도 | 항상 정확 | DECR 누락 시 stale 가능 |
| 호출 빈도 | stock=0일 때만 | - |
| 응답 속도 | 느림 (DB 쿼리) | 빠름 (O(1)) |
| 장애 복구 | 재시작 후 자동 정확 | try-finally 보장으로 stale 위험 낮음 |

`try-finally` 패턴으로 DECR 누락을 방지하기 때문에 stale 위험이 낮다.
단, Redis crash 후 재시작 시 paying 키가 유실되면 0으로 인식 → SOLD_OUT 오판 가능.
이 경우 TicketingStartService 재실행으로 복구한다.

---

## 4. 대기열

```
key:   queue:schedule:{scheduleId}
type:  ZSet (Sorted Set)
value: userId (String)
score: System.currentTimeMillis()  ← 진입 순서 결정
```

### 생명주기

```
[설정 + TTL] QueueService.enter() (대기열 진입 시)
  → ZADD queue:schedule:{id} {timestamp} {userId}
  → expireKey(queueKey, startTime - 10min - now)

[POP] QueueAutoProcessService.collectWindow()
  → ZPOPMIN  ← score(시간) 가장 낮은(먼저 들어온) 유저를 원자적으로 꺼냄
  → window 크기만큼 순차 호출, 꺼낸 유저들은 이후 병렬 처리

[RANK] QueueService.getPosition()
  → ZRANK → 0-indexed rank, +1 하면 순번

[삭제] QueueAutoProcessService.terminateQueue()
  → DELETE (atomic) → 반환값 true인 스레드만 Kafka 발행

[소멸] TTL 만료 (startTime - 10min)
```

### ZPOPMIN 원자성

Redis ZPOPMIN은 atomic 연산이므로 꺼내는 동시에 ZSet에서 제거된다.
`collectWindow()`에서 window 크기만큼 순차 호출해 유저를 수집하고,
수집 완료 후 `processWindowParallel()`에서 병렬로 처리한다.
수집 단계는 단일 스레드에서 순차 실행되므로 중복 처리 없음이 보장된다.

---

## 운영 관점

### 키 만료 후 동작

| 키 | 만료 후 |
|----|--------|
| `cart:count:schedule:{id}` | 이미 마감됨, 조회 시 null 반환 (CartService는 에러 처리) |
| `stock:schedule:{id}` | 티켓팅 마감. EXISTS false → 환불 후 stock INCR 안 함 |
| `queue:schedule:{id}` | 대기열 만료. 남은 대기자는 자동 소멸 (별도 알림 없음) |

### 장애 시나리오

| 상황 | 영향 | 대응 |
|------|------|------|
| Redis 장애 중 addToCart | INCR 실패 → 에러 반환 (no fallback) | 장바구니 추가 불가 |
| Redis 장애 중 enter() | DECR 실패 → 에러 반환 | 구매/대기열 진입 불가 |
| Redis 장애 중 환불 | stock INCR 실패 → 대기열 드레인 안 됨 | 수동 재처리 필요 |
| stock 키 유실 (crash) | 티켓팅 재고 0으로 인식 | TicketingStartService 재실행으로 복구 |