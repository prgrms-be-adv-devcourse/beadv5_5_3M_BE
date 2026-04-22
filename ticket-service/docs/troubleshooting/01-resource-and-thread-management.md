# 리소스·스레드 관리 (RES-001 ~ RES-008)

> 커넥션 풀, 스레드 풀, 타임아웃, 외부 통신 관련 이슈와 해결 방법

---

## RES-001: HikariCP 커넥션 풀 고갈 [CRITICAL → RESOLVED]

**파일:** `application/service/QueueService.java`

### 현상

티켓팅 시작 순간 수백 동시 요청 → `QueueService.enter()`에서 `scheduleRepository.findById()` PK 조회마다 HikariCP 커넥션 획득 → pool(max=10) 즉시 포화 → 30초 후 `SQLTransientConnectionException`.

```
HikariPool-1 - Connection is not available, request timed out after 30001ms
total=10, active=10, idle=0, waiting=81
```

### 근본 원인

`QueueService.enter()`는 `@Transactional`이 없지만 `findById()` 내부에서 커넥션을 획득. 조회 목적인 `seats`, `cookie`, `startTime` 3개 값은 티켓팅 시작 후 불변.

### 해결

**1단계 — 단기 조치:** `maximum-pool-size: 10 → 25`

**2단계 — 근본 해결:** 핫패스 DB 조회 완전 제거
- `TicketingStartService`에서 `seats`, `cookie`, `startTime`을 Redis에 캐싱 (stock/paying과 동일 TTL)
- `QueueService.enter()`에서 `scheduleRepository` 의존성 제거, Redis O(1) 조회로 대체
- `stock` 키 존재 = TICKETING 상태 → DB 상태 확인 불필요

```java
// After: DB 조회 0회
public QueueEntryResponse enter(UUID userId, Long scheduleId) {
    String stockKey = STOCK_KEY_PREFIX + scheduleId;
    if (!cachePort.exists(stockKey)) {
        throw QueueErrorCode.QUEUE_NOT_OPEN.of(scheduleId);
    }
    Long seats = cachePort.getCounter(SEATS_KEY_PREFIX + scheduleId);   // Redis
    Long cookie = cachePort.getCounter(COOKIE_KEY_PREFIX + scheduleId); // Redis
    // ...
}
```

### 효과

| 항목 | Before | After |
|------|--------|-------|
| enter() DB 쿼리 | 요청당 1회 | **0회** |
| HikariCP 점유 | 요청당 1 커넥션 | **0 커넥션** |
| 500 동시 요청 | 30.82초 (타임아웃) | **0.56초** |

### 재발 방지

핫패스 DB 조회 금지 원칙: 초당 수백 요청 이상 엔드포인트에서 DB 조회를 두지 않는다. 불변 값은 초기화 시점에 Redis 캐싱.

---

## RES-002: @Async 스레드풀 미설정 [CRITICAL → RESOLVED]

**파일:** `application/service/QueueAutoProcessService.java`

### 현상

`@Async` 어노테이션이 있으나 `@EnableAsync` 누락 → Spring이 `@Async`를 무시하고 동기 실행. 설령 `@EnableAsync`가 있어도 전용 풀 미지정 시 `SimpleAsyncTaskExecutor` 사용 → 호출마다 새 OS 스레드 생성 → OOM.

### 해결

`AsyncConfig.java` 신규 생성:

```java
@Configuration
public class AsyncConfig {
    @Bean("queueExecutor")
    public Executor queueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("queue-drain-worker-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());        // RES-006
        executor.setWaitForTasksToCompleteOnShutdown(true);                  // RES-007
        executor.setAwaitTerminationSeconds(30);                             // RES-007
        executor.initialize();
        return executor;
    }
}
```

---

## RES-003: CompletableFuture commonPool 사용 [CRITICAL → RESOLVED]

**파일:** `application/service/QueueAutoProcessService.java`

### 현상

`CompletableFuture.runAsync()` Executor 인자 없이 사용 → `ForkJoinPool.commonPool()` → `parallelism = availableProcessors() - 1 = 1` (t3.large 2vCPU) → 사실상 직렬 실행.

### 해결

```java
// Before
CompletableFuture.runAsync(() -> { ... })

// After
CompletableFuture.runAsync(() -> { ... }, queueExecutor)  // 전용 풀 지정
```

`queueExecutor`를 `@Qualifier("queueExecutor")`로 생성자 주입.

---

## RES-004: @Async queueExecutor 풀 고갈 → Kafka 전환 [CRITICAL → RESOLVED]

**파일:** `AsyncConfig.java`, `QueueDrainConsumer.java` (신규), `TicketEventListener.java`

### 현상

60~500건의 `TicketPaidEvent`가 동시에 AFTER_COMMIT → `@Async("queueExecutor")` 제출 → `queueCapacity=20` 초과 → `TaskRejectedException` → 드레인 작업 유실.

내부에서 동일 `queueExecutor` 풀에 `CompletableFuture` 재제출 → 자기 교착(starvation).

### 해결 — Kafka 기반 비동기 분리

`@Async` 완전 제거. Kafka `queue.drain` 토픽으로 분리.

```
[변경 전] handleTicketPaid() → @Async checkAndProcess() → 같은 풀에서 CompletableFuture
[변경 후] handleTicketPaid() → Kafka "queue.drain" 발행
          QueueDrainConsumer(concurrency=4) → checkAndProcess() 동기 실행
```

- `key=scheduleId` 파티셔닝 → 동일 스케줄 순차, 다른 스케줄 병렬
- 컨슈머 스레드 블록 = Kafka 자체 backpressure

---

## RES-005: UserClient 타임아웃 미설정 [HIGH → RESOLVED]

**파일:** `infrastructure/client/user/UserConfig.java`

### 현상

`RestClient.builder()` 기본 설정 = 타임아웃 없음. user-service 지연 시 호출 스레드 무한 블록 → `@Transactional` 내에서 DB 커넥션도 함께 점유 → HikariCP 풀 고갈 연쇄.

### 해결

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(3));
factory.setReadTimeout(Duration.ofSeconds(5));
return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
```

---

## RES-006: AsyncConfig RejectedExecutionHandler 미설정 [MEDIUM → RESOLVED]

**파일:** `infrastructure/config/AsyncConfig.java`

### 현상

`ThreadPoolTaskExecutor` 기본 reject policy = `AbortPolicy` → `queueCapacity` 초과 시 `RejectedExecutionException` → task 유실 → stock 불일치.

### 해결

```java
executor.setRejectedExecutionHandler(new CallerRunsPolicy());
```

`CallerRunsPolicy`: 큐가 가득 차면 호출 스레드가 직접 실행 → backpressure 효과.

---

## RES-007: AsyncConfig Graceful Shutdown 미설정 [MEDIUM → RESOLVED]

**파일:** `infrastructure/config/AsyncConfig.java`

### 현상

앱 종료 시 `processWindowParallel()` 실행 중인 `CompletableFuture`가 강제 종료 → stock DECR 후 `tryPurchase` 미완료 → 재고 불일치.

### 해결

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(30);
```

---

## RES-008: UserClient 4xx 응답 미처리 [LOW → RESOLVED]

**파일:** `infrastructure/client/user/UserClient.java`

### 현상

user-service가 잔액 부족 시 402 반환 → `HttpClientErrorException` 발생 → `RuntimeException`으로 래핑 → `GlobalExceptionHandler` 미인식 → 500 반환.

`QueuePurchaseProcessor`는 `response.flag()==false`를 기대했지만 예외로 도달 불가.

### 해결

모든 `HttpClientErrorException`(4xx)을 `flag=false`로 처리. 402(잔액 부족), 404(유저 없음) 등 어떤 4xx든 쿠키 차감 실패로 통일.

```java
} catch (HttpClientErrorException e) {
    // 4xx: 잔액 부족(402), 유저 없음(404) 등 — 차감 실패로 처리
    return new DeductCookieResponse(null, null, null, false);  // flag=false
}
```