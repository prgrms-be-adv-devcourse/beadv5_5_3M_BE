# 대기열 드레인 전략 — 순차 vs 윈도우 병렬

## 대기열이란?

티켓팅 시작 후 재고(stock)가 0이 된 시점부터 유저를 순서대로 줄 세우는 구조.
환불이나 결제 실패로 재고가 복구되면 대기 중인 유저를 자동으로 처리한다.

```
재고 있음  → 즉시 구매 (DECR → tryPurchase)
재고 없음  → 대기열 진입 (ZADD queue)
재고 복구  → 대기열 드레인 자동 실행 (@Async checkAndProcess)
```

### 관련 Redis 키

| 키 | 타입 | 역할 |
|----|------|------|
| `stock:schedule:{id}` | Counter | 남은 구매 가능 좌석 수 |
| `queue:schedule:{id}` | ZSet | 대기 유저 목록 (score = 진입 timestamp) |

---

## 드레인이란?

재고가 복구될 때 대기열에서 유저를 꺼내 구매를 시도하는 과정.

```
환불 발생
  → stock INCR (재고 복구)
  → @Async checkAndProcess() 호출
      → drainQueue(): 대기 유저 구매 처리
      → checkTermination(): 대기열 종료 여부 판단
```

`@Async`이므로 환불 API 응답은 드레인 완료를 기다리지 않고 즉시 반환된다.

---

## V1 — 순차 처리 (기존)

### 구조

```
drainQueue():
  while (true):
    stockAfterDecr = DECR(stock)         ① stock 선점
    if stockAfterDecr < 0: INCR; break   ② 재고 소진 시 종료

    userId = ZPOPMIN(queue)              ③ 대기열 1명 꺼냄
    if userId == null: INCR; break       ④ 대기열 빔

    tryPurchase(userId)                  ⑤ HTTP 결제 호출 (~100ms)
    // 완료 후 다음 루프
```

### 타임라인 (stock=3, 대기 3명)

```
t=0ms    DECR → pop A → tryPurchase(A) 시작
t=100ms  A 완료 → DECR → pop B → tryPurchase(B) 시작
t=200ms  B 완료 → DECR → pop C → tryPurchase(C) 시작
t=300ms  C 완료 → 루프 종료
총 소요: ~300ms
```

---

## V2 — 윈도우 병렬 처리 (현재)

### 구조

```
drainQueue():
  while (true):
    stock     = GET(stock)               ① 현재 재고 확인
    queueSize = ZCARD(queue)             ② 현재 대기 수 확인
    if stock <= 0 or queueSize == 0: break

    window = min(stock, queueSize)       ③ 윈도우 크기 결정

    // collectWindow — 순차 수집 (원자성 보장)
    tasks = []
    for i in 0..window:
      stockAfterDecr = DECR(stock)       ④ stock 1개 선점
      userId = ZPOPMIN(queue)            ⑤ 대기자 1명 꺼냄
      tasks.add(userId, ticketNum)

    // processWindowParallel — 병렬 실행
    CompletableFuture.allOf(
      tasks.map(t -> runAsync(() -> tryPurchase(t)))  ⑥ 동시 실행
    ).join()                             ⑦ 윈도우 전체 완료 대기

    // 실패 유저는 stock 복구됨 → 다음 루프에서 재계산
```

### 타임라인 (stock=3, 대기 3명)

```
t=0ms    window=3 결정 → DECR×3, ZPOPMIN×3 수집 (수ms)
t=~5ms   tryPurchase(A) ┐
         tryPurchase(B) ├─ 동시 실행
         tryPurchase(C) ┘
t=~105ms 전체 완료 → 루프 종료
총 소요: ~105ms
```

---

## 차이점 비교

| 항목 | V1 순차 | V2 윈도우 병렬 |
|------|---------|--------------|
| **처리 방식** | 1명씩 순서대로 | window 크기만큼 동시 |
| **소요 시간** (stock=N) | N × HTTP 지연 | 1 × HTTP 지연 + 수집 오버헤드 |
| **stock=1일 때** | 차이 없음 | 차이 없음 |
| **원자성 보장** | DECR-first 방식 | DECR-first 방식 동일 |
| **실패 처리** | 다음 루프에서 재시도 | 같은 윈도우 내 병렬 실패 → stock 복구 → 다음 윈도우 |
| **스레드** | @Async 1개 | @Async 1개 + ForkJoinPool N개 |
| **구현 복잡도** | 낮음 | 중간 |

---

## 장단점

### V1 순차 처리

**장점**
- 구현이 단순하고 흐름을 한눈에 파악하기 쉽다
- 각 tryPurchase 결과를 즉시 반영해 다음 결정에 사용 가능

**단점**
- stock이 클수록 드레인 시간이 선형 증가 (stock=10이면 ~1,000ms)
- 대규모 환불 동시 발생 시 늦게 처리된 대기자는 오래 기다림

### V2 윈도우 병렬 처리

**장점**
- stock과 무관하게 드레인 시간이 HTTP 호출 1번 수준으로 수렴
- 대규모 환불 동시 발생 시 대기자 경험이 균등하게 빠름

**단점**
- 실패 시 다음 윈도우에서 재처리 → stock 복구 후 루프가 한 번 더 돈다
- `collectWindow`와 `processWindowParallel` 사이에 다른 `checkAndProcess` 스레드가 끼어들면 window 크기가 실제보다 크게 잡힐 수 있음 (DECR이 초과될 경우 음수 감지 후 즉시 복구되므로 정확성에는 문제 없음)

---

## 동시성 안전성

두 방식 모두 아래 조건으로 동시성이 보장된다.

```
1. DECR 원자성
   Redis DECR은 단일 명령어 → 두 스레드가 동시 호출해도 서로 다른 값 반환
   음수가 된 스레드만 INCR 복구 후 중단

2. ZPOPMIN 원자성
   꺼냄 + 삭제가 atomic → 같은 유저를 두 번 처리하는 경우 없음

3. terminateQueue 중복 방지
   DEL 반환값(boolean)으로 첫 번째 스레드만 Kafka 발행
```

V2에서 `collectWindow`는 단일 스레드 순차 실행이므로
DECR과 ZPOPMIN 쌍이 항상 1:1 대응된다.
병렬성은 수집 이후 tryPurchase 단계에서만 발생한다.

---

## 실패 시나리오별 동작

### 쿠키 부족 (tryPurchase flag=false)

```
V1: stock INCR 복구 → 다음 루프에서 다음 대기자 처리
V2: stock INCR 복구 → 윈도우 완료 후 다음 루프에서 새 window 계산
    (복구된 stock만큼 다음 대기자로 채워짐)
```

### tryPurchase 예외

```
V1: catch → stock INCR 복구 → 루프 계속 (해당 userId 소실)
V2: catch → stock INCR 복구 → 윈도우 내 다른 유저는 영향 없음
```

### 대기열이 다 소진되었는데 stock이 남을 때

```
두 방식 모두:
  ZPOPMIN → null → stock INCR 복구 → break
  checkTermination: stock>0, queue=0 → 종료 조건 미충족 → 대기
```

---

## 설계 결정 — SOLD_OUT 판단에 Redis paying 카운터 사용

### 현재 구현

stock=0일 때 대기열 진입 허용 여부를 `paying:schedule:{id}` Redis 카운터로 판단한다.

```java
// QueueService.enter()
Long payingCount = cachePort.getCounter(PAYING_KEY_PREFIX + scheduleId);
if (payingCount == null || payingCount <= 0) throw QueueErrorCode.SOLD_OUT.of(scheduleId);
// payingCount > 0 → 결제 진행 중인 유저가 있음 → 대기열 진입 허용
```

`paying > 0`은 "누군가 현재 결제 시도 중이고, 실패 시 stock이 복구될 수 있다"는 의미다.

### paying 카운터 관리

```
INCR 시점: stock DECR 성공 + 유저 할당 (tryPurchase 시작 전)
DECR 시점: tryPurchase 완료 (성공/실패/예외 모든 경로 — try-finally 보장)
관리 위치: QueueService, QueueAutoProcessService.collectWindow/processWindowParallel
```

**전제 조건 충족 여부:**
TICKETING 단계에서 RESERVED 티켓은 queue tryPurchase 중인 것만 존재한다.
Case A RESERVED 티켓은 TicketingStartService에서 전부 삭제되기 때문이다.
→ `paying 카운터 = RESERVED count` 등식 성립 ✅

### DB COUNT vs Redis paying 카운터 비교

| 항목 | DB COUNT | Redis paying (현재) |
|------|----------|-------------------|
| 정확도 | 항상 정확 (DB가 진실) | try-finally로 stale 위험 낮음 |
| 장애 복구 | 재시작 후 자동 정확 | 키 유실 시 TicketingStartService 재실행 |
| 응답 속도 | 느림 (DB 쿼리) | 빠름 (O(1)) |
| 호출 빈도 | stock=0일 때만 실행 | - |

`try-finally` 패턴으로 DECR 누락을 방지해 stale 위험을 최소화했다.
Redis crash 후 재시작 시 paying 키가 유실되면 SOLD_OUT 오판 가능하나,
TicketingStartService 재실행으로 paying=0으로 초기화되어 복구된다.
