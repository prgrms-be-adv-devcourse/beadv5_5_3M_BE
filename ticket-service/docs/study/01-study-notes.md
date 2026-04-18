# 학습 노트 — 이 프로젝트에서 공부할 만한 기술 포인트

## 1. Spring AOP Self-Invocation 문제

### 핵심 개념

Spring의 `@Transactional`, `@Async`는 **AOP 프록시** 기반으로 동작한다.
같은 클래스 안에서 자기 자신의 메서드를 호출하면 프록시를 거치지 않아 어노테이션이 무시된다.

### 이 프로젝트에서 만난 상황

`QueueAutoProcessService`가 `@Async`이고, 내부에서 `@Transactional` 메서드를 필요로 한다.
같은 클래스에 두면:

```java
// ❌ 이렇게 하면 @Transactional이 동작 안 함
@Service
public class QueueAutoProcessService {

    @Async
    public void checkAndProcess(Long scheduleId) {
        tryPurchase(...); // this.tryPurchase() → 프록시 우회 → @Transactional 무시
    }

    @Transactional  // 적용 안 됨
    public Optional<TicketResponse> tryPurchase(...) { ... }
}
```

### 해결책: 별도 Bean으로 분리

```java
// ✅ QueuePurchaseProcessor를 별도 @Component로 분리
@Component
public class QueuePurchaseProcessor {

    @Transactional  // 외부 Bean 호출 → 프록시 정상 작동
    public Optional<TicketResponse> tryPurchase(...) { ... }
}

@Service
public class QueueAutoProcessService {

    private final QueuePurchaseProcessor purchaseProcessor; // DI로 주입

    @Async
    public void checkAndProcess(Long scheduleId) {
        purchaseProcessor.tryPurchase(...); // 프록시 통해 호출 → @Transactional 적용됨
    }
}
```

**핵심:** Spring Bean을 주입받아 호출해야 AOP 프록시를 경유한다.

---

## 2. setRollbackOnly() — 예외 없이 트랜잭션 롤백

### 핵심 개념

`@Transactional` 메서드에서 예외를 `throw`하면 트랜잭션이 롤백된다.
하지만 **호출자에게 예외를 전파하고 싶지 않은데 롤백은 해야 할 때** `setRollbackOnly()`를 쓴다.

### 이 프로젝트에서 만난 상황

`tryPurchase()`가 쿠키 부족으로 실패할 때:
- 티켓 save는 롤백해야 함 (DB에 저장된 RESERVED 티켓 취소)
- 하지만 `drainQueue()`의 while 루프는 **계속 돌아야** 함 (다음 대기자 시도)

예외를 throw하면 루프가 중단되어 버린다.

```java
@Transactional
public Optional<TicketResponse> tryPurchase(...) {
    ticketRepository.save(ticket);  // 저장

    DeductCookieResponse response = userPort.deductTicketFee(...);

    if (!response.flag()) {
        cachePort.increment(stockKey);  // Redis 복구 (트랜잭션 밖이라 롤백 안 됨)

        // 예외 없이 트랜잭션만 롤백 마킹
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        return Optional.empty();  // 호출자는 정상 반환값 수신 → 루프 계속
    }
    // ...
}
```

```java
// drainQueue: 예외 없이 다음 사람으로 넘어감
purchaseProcessor.tryPurchase(scheduleId, userId, ticketNum);
// Optional.empty() 반환 → continue (루프 계속)
```

**핵심:** `setRollbackOnly()`는 현재 트랜잭션을 "실패로 마킹"만 할 뿐,
메서드는 정상적으로 리턴하고 트랜잭션 종료 시점에 롤백된다.

---

## 3. Redis 원자적 연산으로 동시성 제어

### 핵심 개념

여러 스레드/서버가 동시에 같은 재고에 접근할 때, DB 락 없이 Redis 단일 명령어의 원자성으로 동시성을 제어한다.

### DECR로 재고 선점

```
스레드 A: DECR stock → 2 (성공, 구매 진행)
스레드 B: DECR stock → 1 (성공, 구매 진행)
스레드 C: DECR stock → 0 (성공, 구매 진행)
스레드 D: DECR stock → -1 (실패, 즉시 INCR 복구)
```

DECR은 Redis 서버에서 원자적으로 실행되므로
두 스레드가 동시에 호출해도 서로 다른 값을 받는다.
음수가 된 스레드만 실패 처리하면 정확히 재고 수만큼만 구매가 허용된다.

```java
Long stockAfterDecr = cachePort.decrement(stockKey);  // 원자적
if (stockAfterDecr < 0) {
    cachePort.increment(stockKey);  // 복구
    // 재고 없음 처리
}
```

### ZPOPMIN으로 대기열 원자적 처리

```
대기열: [A(시간:100), B(시간:200), C(시간:300)]

collectWindow (단일 스레드, 순차):
  ZPOPMIN → A  (ZSet에서 원자적으로 꺼냄)
  ZPOPMIN → B
  ZPOPMIN → C

processWindowParallel (병렬):
  tryPurchase(A) ┐
  tryPurchase(B) ├─ 동시 실행 (queueExecutor, core=4, max=8)
  tryPurchase(C) ┘
```

`ZPOPMIN`은 꺼내는 동시에 삭제까지 원자적으로 수행한다.
수집(collectWindow)은 단일 스레드 순차 실행이므로 중복 pop이 발생하지 않고,
이후 병렬 처리(processWindowParallel)는 이미 분리된 userId들을 각자 처리한다.

### Redis atomic DEL로 중복 이벤트 방지

```java
// 여러 스레드가 동시에 terminateQueue 진입해도
boolean deleted = cachePort.delete(queueKey);  // DEL: 삭제된 경우 true, 이미 없으면 false
if (!deleted) return;  // 다른 스레드가 먼저 처리함

// 첫 번째 스레드만 Kafka 발행
eventPublisherPort.publish("queue.terminated", ...);
```

---

## 4. @Async → Kafka 기반 비동기 분리

### 핵심 개념

`@Async`가 붙은 메서드는 호출 즉시 리턴하고, 실제 실행은 별도 스레드풀에서 수행된다.
하지만 동시 이벤트가 대량 발생하면 스레드풀 고갈(`TaskRejectedException`) 문제가 있다.

### 이 프로젝트의 변천

**초기 설계 (@Async):**
```
TicketEventListener.handleTicketRefunded()  [AFTER_COMMIT]
  → @Async("queueExecutor") checkAndProcess()
```

동시 결제 60~500건 → `queueCapacity=20` 초과 → `TaskRejectedException` → 드레인 유실.
또한 `checkAndProcess()` 내부에서 동일 `queueExecutor`에 `CompletableFuture` 재제출 → 자기 교착(starvation).

**현재 설계 (Kafka 기반):**
```
TicketEventListener.handleTicketRefunded()  [AFTER_COMMIT]
  → Kafka "queue.drain" 발행 (동기, fire-and-forget)
      ↓ (별도 Kafka consumer 스레드)
  QueueDrainConsumer(concurrency=4) → checkAndProcess() 동기 실행
```

- `key=scheduleId` 파티셔닝 → 동일 스케줄은 단일 파티션에서 순차 처리
- Kafka consumer 스레드가 `checkAndProcess()` 완료까지 블록 → 자연스러운 backpressure
- `queueExecutor`는 `drainQueue()` 내부 `CompletableFuture` 병렬 처리 전용으로만 사용

### 교훈

AFTER_COMMIT 리스너에서 `@Async`로 무거운 작업을 직접 제출하면 이벤트 폭주 시 풀 고갈 위험이 있다.
메시지 큐(Kafka)로 분리하면 consumer 수로 처리량을 제어할 수 있고, 자체 backpressure가 작동한다.

---

## 5. 헥사고날 아키텍처 (Ports & Adapters)

### 핵심 개념

비즈니스 로직(domain, application)이 인프라(DB, Redis, Kafka)에 의존하지 않도록
**인터페이스(Port)**로 경계를 만들고, 구현체(Adapter)를 인프라 레이어에 둔다.

### 이 프로젝트의 레이어

```
presentation/   ← HTTP 요청 수신 (Controller)
    │
application/    ← 비즈니스 로직 (Service, UseCase, Port 인터페이스)
    │
domain/         ← 순수 도메인 모델 (Entity, 상태 전이 메서드)
    │
infrastructure/ ← Port 구현체 (JPA, Redis, Kafka, RestClient)
```

### Port 인터페이스 예시

```java
// application/port/out/CachePort.java  ← 인터페이스 (application 레이어)
public interface CachePort {
    Long increment(String key);
    Long decrement(String key);
    // ...
}

// infrastructure/caching/adapter/RedisCacheAdapter.java  ← 구현체 (infrastructure 레이어)
@Component
public class RedisCacheAdapter implements CachePort {
    private final StringRedisTemplate redisTemplate;

    @Override
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
}
```

**장점:** `CachePort`를 구현하는 Mock을 만들면 Redis 없이 Service 테스트 가능.
실제 Redis를 Memcached로 바꿔도 `RedisCacheAdapter`만 교체하면 됨.

### 의존성 방향 규칙

```
application → domain       ✅ (도메인 모델 사용)
infrastructure → application ✅ (Port 인터페이스 구현)
application → infrastructure ❌ (절대 금지 — 역방향 의존)
```

---

## 6. @TransactionalEventListener — DB 정합성 보장

→ 자세한 내용은 [`transactional-event-listener-pattern`](../reference/infra/04-transactional-event-listener-pattern.md) 참고

**한 줄 요약:** DB 커밋 후에만 Redis/Kafka 작업을 실행해
트랜잭션 롤백 시 이벤트가 발행되지 않도록 보장하는 패턴.

---

## 정리

| 개념 | 핵심 키워드 | 관련 코드 |
|------|------------|----------|
| AOP Self-Invocation | 프록시, 별도 Bean 분리 | `QueuePurchaseProcessor` |
| setRollbackOnly | 예외 없는 롤백, 루프 유지 | `QueuePurchaseProcessor.tryPurchase()` |
| Redis 원자적 연산 | DECR/ZPOPMIN/DEL 원자성 | `QueueAutoProcessService.drainQueue()` |
| @Async → Kafka 전환 | 스레드풀 고갈 방지, backpressure | `QueueDrainConsumer`, `QueueAutoProcessService` |
| 헥사고날 아키텍처 | Port/Adapter, 의존성 역전 | `CachePort`, `UserPort`, `EventPublisherPort` |
| @TransactionalEventListener | AFTER_COMMIT, 정합성 | `TicketEventListener`, `ScheduleEventListener` |