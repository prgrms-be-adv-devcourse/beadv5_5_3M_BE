# 트러블슈팅: @Async 스레드풀 미설정 / CompletableFuture commonPool / 스트리밍 Job 상태 가드 누락

## 개요

Gemini AI 코드 리뷰(PR: feature/self-payment-foundation)에서 발견된 3개의 CRITICAL 이슈.
배포 전 코드 분석 단계에서 식별되어 실제 장애로 이어지지 않았으나,
단일 t3.large(2 vCPU, 8GB) 환경에서 운영 시 반드시 발생할 수 있는 문제들이다.

| 이슈 ID | 심각도 | 위치 | 증상 |
|---------|--------|------|------|
| RES-002 | CRITICAL | `QueueAutoProcessService` | `@Async` 실제 비동기 미동작, 스레드 무제한 생성 |
| RES-003 | CRITICAL | `QueueAutoProcessService` | 윈도우 병렬처리가 사실상 직렬 실행 |
| TKT-005 | CRITICAL | `StreamingStartService`, `StreamingFinishService` | Quartz Job 재실행 시 역전이 및 무한 루프 |

---

## 이슈 1 — RES-002: `@Async` 스레드풀 미설정

### 문제 코드

```java
// QueueAutoProcessService.java (수정 전)
@Async  // ← qualifier 없음, @EnableAsync도 없음
public void checkAndProcess(Long scheduleId) { ... }
```

### 근본 원인

1. **`@EnableAsync` 누락**: 애플리케이션 어디에도 `@EnableAsync`가 선언되지 않았다.
   Spring은 `@Async`를 무시하고 **동기(동일 스레드)로 실행**한다.
   즉, `RefundService` / `TicketEventListener`에서 `checkAndProcess()`를 호출하면
   해당 트랜잭션 커밋 스레드가 대기열 드레인이 끝날 때까지 블록된다.

2. **전용 스레드풀 미설정**: `@EnableAsync`가 있더라도 `@Async` 단독 사용 시
   Spring Boot는 `SimpleAsyncTaskExecutor`를 기본으로 사용한다.
   이 Executor는 호출마다 새 OS 스레드를 생성하며 **풀링이 없다**.
   - 환불/결제 이벤트가 연속 발생하면 스레드가 무제한으로 늘어남
   - 2 vCPU에서 수백 개 스레드 = 컨텍스트 스위칭 오버헤드 → CPU 버스트 크레딧 급소진
   - 스레드 스택 메모리(기본 512KB~1MB/개) 누적 → OOM 가능

### 해결

**`AsyncConfig.java` 신규 생성**

```java
@Configuration
@EnableAsync  // ← 반드시 선언
public class AsyncConfig {

    @Bean("queueExecutor")
    public Executor queueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);    // t3.large 2 vCPU 기준
        executor.setMaxPoolSize(4);     // 버스트 허용
        executor.setQueueCapacity(20);  // backpressure — 초과 시 CallerRunsPolicy
        executor.setThreadNamePrefix("queue-async-");
        executor.initialize();
        return executor;
    }
}
```

**`QueueAutoProcessService.java` 수정**

```java
// Before
@Async
public void checkAndProcess(Long scheduleId) { ... }

// After
@Async("queueExecutor")
public void checkAndProcess(Long scheduleId) { ... }
```

### 스레드풀 사이징 근거

| 항목 | 값 | 이유 |
|------|-----|------|
| corePoolSize | 2 | t3.large vCPU 수 |
| maxPoolSize | 4 | 단기 버스트 처리, vCPU × 2 |
| queueCapacity | 20 | checkAndProcess 호출 빈도 제한(backpressure) |

---

## 이슈 2 — RES-003: `CompletableFuture.runAsync()` 기본 commonPool 사용

### 문제 코드

```java
// QueueAutoProcessService.java (수정 전)
List<CompletableFuture<Void>> futures = tasks.stream()
        .map(task -> CompletableFuture.runAsync(() -> {  // ← Executor 없음
            ...
        }))
        .toList();
```

### 근본 원인

`Executor` 인자 없이 `CompletableFuture.runAsync()` 사용 시 `ForkJoinPool.commonPool()`을 사용한다.

```
commonPool 병렬도 = Runtime.getRuntime().availableProcessors() - 1
                 = 2 - 1 = 1  (t3.large 2 vCPU)
```

- 윈도우 크기가 5라도 commonPool 스레드가 1개이므로 **사실상 직렬 실행**
- 대기열 드레인 속도가 설계 의도의 1/N로 저하
- `@Async("queueExecutor")` 스레드가 `commonPool`을 기다리면서
  `allOf().join()`에서 스레드 교착(starvation) 가능성

### 해결

```java
// After: queueExecutor를 두 번째 인자로 전달
List<CompletableFuture<Void>> futures = tasks.stream()
        .map(task -> CompletableFuture.runAsync(() -> {
            try {
                purchaseProcessor.tryPurchase(scheduleId, task.userId(), task.ticketNum());
            } catch (Exception e) {
                cachePort.increment(stockKey);
            } finally {
                cachePort.decrement(payingKey);
            }
        }, queueExecutor))  // ← 전용 풀 지정
        .toList();
```

`queueExecutor`를 `QueueAutoProcessService` 생성자로 주입 (`@Qualifier("queueExecutor")`).

### 개선 효과

| 항목 | Before | After |
|------|--------|-------|
| 윈도우 병렬도 | 1 (commonPool, 2 vCPU 기준) | 최대 4 (maxPoolSize) |
| 스레드 교착 위험 | @Async 풀 ↔ commonPool 혼용 | 단일 queueExecutor로 통합 |
| 스레드 수 제어 | 불가 (commonPool 공유) | corePoolSize~maxPoolSize 범위 내 제어 |

---

## 이슈 3 — TKT-005: StreamingStart/Finish 상태 전이 가드 누락

### 문제 코드

```java
// StreamingStartService.java (수정 전)
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));
    schedule.startStreaming();  // ← 상태 확인 없이 바로 호출
    scheduleRepository.save(schedule);
}
```

`Schedule.startStreaming()` 도메인 메서드 내부에는 가드가 있다:

```java
public void startStreaming() {
    if (this.status != ScheduleStatus.TICKETING) {
        throw ScheduleErrorCode.NOT_IN_TICKETING.of(this.id);  // 예외 throw
    }
    this.status = ScheduleStatus.STREAMING;
}
```

### 왜 서비스 레이어 가드가 추가로 필요한가

도메인 가드는 예외를 던지고, 이 예외는 `StreamingStartQuartzJob`까지 전파된다.

```java
// StreamingStartQuartzJob
protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    streamingStartUseCase.execute(scheduleId);  // 예외 발생
}
```

Quartz가 `JobExecutionException`을 받으면 Job을 **FAILED** 상태로 기록하고,
misfire 설정에 따라 **재시도 트리거가 발동**될 수 있다.
→ 재시도마다 동일 예외 반복 → FAILED 잡 무한 누적 → Quartz JDBC JobStore 오염

반면 서비스 레이어에서 early return하면 Job이 **정상 완료(COMPLETE)**로 기록되어
Quartz 재시도가 발생하지 않는다.

### 발생 가능 시나리오

| 시나리오 | 동작 |
|---------|------|
| Quartz 클러스터 페일오버 | 다른 노드가 같은 Job을 재실행 → 이미 STREAMING인 스케줄에 startStreaming() 호출 |
| `JobTestController` 수동 트리거 | dev 환경에서 실수로 중복 실행 |
| misfire 복구 | Quartz 재기동 시 놓친 트리거 재실행 |

### 해결

```java
// StreamingStartService.java (수정 후)
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));
    if (schedule.getStatus() != ScheduleStatus.TICKETING) {
        log.warn("스트리밍 시작 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
        return;  // ← 정상 완료로 처리 → Quartz 재시도 없음
    }
    schedule.startStreaming();
    scheduleRepository.save(schedule);
    log.info("스트리밍 시작 - scheduleId={}", scheduleId);
}
```

```java
// StreamingFinishService.java (수정 후)
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));
    if (schedule.getStatus() != ScheduleStatus.STREAMING) {
        log.warn("스트리밍 종료 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
        return;
    }
    schedule.finishStreaming();
    scheduleRepository.save(schedule);
    log.info("스트리밍 종료 - scheduleId={}", scheduleId);
}
```

---

## 변경 파일 목록

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `infrastructure/config/AsyncConfig.java` | 신규 | `@EnableAsync` + `queueExecutor` 빈 정의 |
| `application/service/QueueAutoProcessService.java` | 수정 | `@Async("queueExecutor")`, `runAsync(task, queueExecutor)`, 생성자 주입 |
| `application/service/StreamingStartService.java` | 수정 | TICKETING 상태 가드 + early return |
| `application/service/StreamingFinishService.java` | 수정 | STREAMING 상태 가드 + early return |

---

## 재발 방지 원칙

1. **`@Async` 사용 시 항상 전용 Bean 지정**: `@Async` 단독 사용은 `SimpleAsyncTaskExecutor`로
   fallback되므로, 반드시 `@Async("빈이름")`으로 명시한다.

2. **`CompletableFuture.runAsync()` Executor 필수 지정**: 특히 제한된 CPU(t3 계열)에서
   commonPool 사용은 병렬도=1이 될 수 있다. 항상 전용 Executor를 전달한다.

3. **Quartz Job의 서비스 레이어 멱등성 보장**: 도메인 예외로 Quartz Job을 FAILED시키면
   misfire 재시도 루프를 만든다. 상태가 이미 전이된 경우 서비스 레이어에서 early return하여
   Job을 정상 완료(COMPLETE)로 처리한다.

---

## 관련 문서

- 대기열 드레인 전략 → [`15-queue-drain-strategy.md`](./15-queue-drain-strategy.md)
- Quartz Job 체이닝 → [`05-quartz-job-chaining.md`](./05-quartz-job-chaining.md)
- HikariCP 커넥션 풀 고갈 → [`16-troubleshooting-hikaricp-exhaustion.md`](./16-troubleshooting-hikaricp-exhaustion.md)
