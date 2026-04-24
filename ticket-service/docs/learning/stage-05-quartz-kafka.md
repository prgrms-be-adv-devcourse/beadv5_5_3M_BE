# Stage 5 — Quartz Job + Kafka Consumer

> **목표**: 이 서비스의 **진입점** 두 종류 — 시간 기반(Quartz)와 이벤트 기반(Kafka Consumer)을 한 번에 조망한다.
> Stage 2의 Service는 "누가 호출하는가?"의 답이 Stage 5에서 모인다.
> **예상 소요**: 1일

---

## 0. 진입점 지도

```
┌─ 시간 기반 (Quartz) ─────────────────────────────────┐
│                                                      │
│  CartCloseQuartzJob     →  CartCloseService          │
│  TicketingStartQuartzJob → TicketingStartService     │
│  StreamingStartQuartzJob → StreamingStartService     │
│  StreamingFinishQuartzJob → StreamingFinishService   │
│  ReviewAuthQuartzJob    →  ReviewAuthService         │
│  DailyTicketFeeProvideScheduler (1AM Cron) →         │
│                            ProvideTicketFeeService   │
└──────────────────────────────────────────────────────┘

┌─ 이벤트 기반 (Kafka Consumer) ───────────────────────┐
│                                                      │
│  ScheduleEventConsumer (movie.schedule.confirmed)    │
│    → Schedule 엔티티 생성                            │
│  QueueDrainConsumer (queue.drain, concurrency=4)     │
│    → QueueAutoProcessService.checkAndProcess         │
└──────────────────────────────────────────────────────┘

┌─ HTTP (REST Controller) ─────────────────────────────┐
│   CartController, TicketController, QueueController  │
│   → CartService, SelfPaymentService, QueueService    │
└──────────────────────────────────────────────────────┘
```

---

## 1. Quartz 기본기 리마인더

| 개념 | 역할 |
|------|------|
| **JobDetail** | 어떤 Job 클래스를 실행할지 + JobDataMap(파라미터) |
| **Trigger** | 언제 실행할지 (SimpleTrigger / CronTrigger) |
| **Scheduler** | JobDetail + Trigger를 묶어 실행 관리 |
| **JobStore** | prod에서 DB에 저장 (클러스터 노드 간 공유) |
| `@DisallowConcurrentExecution` | 같은 JobKey의 Job이 겹쳐 실행되지 않음 |

`QuartzSchedulerAdapter` 가 JobDetail/Trigger를 조립해 `scheduler.scheduleJob(job, trigger)` — Stage 3에서 본 그대로.

---

## 2. Quartz Job 5종 — 같은 뼈대

모든 Job이 `QuartzJobBean`을 상속하고 `@DisallowConcurrentExecution`을 붙임. 예시 하나만 보면 나머지는 동형.

`infrastructure/scheduling/TicketingStartQuartzJob.java:10-32`:

```java
@Slf4j
@DisallowConcurrentExecution
public class TicketingStartQuartzJob extends QuartzJobBean {

    public static final String SCHEDULE_ID_KEY = "scheduleId";

    private TicketingStartUseCase ticketingStartUseCase;

    public void setTicketingStartUseCase(TicketingStartUseCase ticketingStartUseCase) {
        this.ticketingStartUseCase = ticketingStartUseCase;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong(SCHEDULE_ID_KEY);
        try {
            ticketingStartUseCase.execute(scheduleId);
        } catch (Exception e) {
            log.error("TicketingStartQuartzJob 실패 - scheduleId={}", scheduleId, e);
            throw new JobExecutionException(e);
        }
    }
}
```

**주목할 점**:
- **DI 방식이 setter**. Quartz가 `Job` 인스턴스를 직접 new 하기 때문에 Spring의 생성자 주입이 안 먹음 → `QuartzJobBean` + `AutowiringSpringBeanJobFactory`가 setter 주입으로 처리 (Quartz 설정).
- **`SCHEDULE_ID_KEY`** — JobDataMap에 담긴 scheduleId를 꺼냄. `QuartzSchedulerAdapter`가 등록 시 `usingJobData(...)`로 넣어둠.
- **예외 처리**: catch한 뒤 **다시 `JobExecutionException`** throw → Quartz가 "Job 실패"로 기록. 트리거 재시도 정책에 따라 재실행 가능.

### 5개 Job 비교

| Job 클래스 | 호출하는 UseCase | 트리거 시점 |
|------------|------------------|-------------|
| `CartCloseQuartzJob` | `CartCloseUseCase` | `ticketingTime - 24h` |
| `TicketingStartQuartzJob` | `TicketingStartUseCase` | `ticketingTime` |
| `StreamingStartQuartzJob` | `StreamingStartUseCase` | `startTime` |
| `StreamingFinishQuartzJob` | `StreamingFinishUseCase` | `endTime` |
| `ReviewAuthQuartzJob` | `ReviewAuthUseCase` | `startTime - 10m` |

**주의**: `ReviewAuth` 는 `startTime - 10m` (streaming-service 대기실 개방 시점)에,
`StreamingStart` 는 `startTime` 에 트리거. 서로 다른 JobKey + 다른 시각이므로 충돌 없음.
Entitlement 사본이 streaming-service 에 먼저 적재되어야 LOBBY_OPEN 시 WebSocket CONNECT 가 통과됨.

---

## 3. `@DisallowConcurrentExecution`의 의미

**보호 대상**: 같은 `JobKey` (동일 `jobName + group`)의 Job이 **중복 실행되지 않음**.

### 어떤 상황이 방지되나?

시나리오: `TicketingStartQuartzJob_42`가 실행 중인데, 어떤 이유로 (misfire, 수동 트리거) 같은 Job이 다시 기동 시도.

- **없다면**: 두 스레드가 동시에 `TicketingStartService.execute(42)` 호출 → batch cleanup 중복, 상태 전이 2번 시도 → invariant 깨짐.
- **있다면**: 두 번째 실행이 **대기열에 들어가** 첫 번째 완료 후 실행 (또는 misfire 정책에 따라 skip).

### Kafka key 파티셔닝과의 차이

| 기법 | 보장 범위 | 쓰이는 곳 |
|------|-----------|-----------|
| `@DisallowConcurrentExecution` | 같은 JobKey의 중복 실행 방지 | Quartz Job |
| Kafka key 파티셔닝 | 같은 key의 메시지가 순차 처리 | `queue.drain` scheduleId key |
| DB 락 (JobStore) | 클러스터 노드 간 중복 실행 방지 (prod) | Quartz prod 모드 |

→ **서로 다른 도메인의 동시성 제어**. 셋 다 쓰임.

---

## 4. ScheduleEventConsumer — inbound Kafka

`infrastructure/messaging/consumer/ScheduleEventConsumer.java:25-59`

```java
@Transactional
@KafkaListener(topics = KafkaTopics.SCHEDULE_CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
public void consume(String message) {
    try {
        ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);

        if (scheduleRepository.existsById(request.scheduleId())) {
            log.warn("중복 스케줄 수신 무시 - scheduleId={}", request.scheduleId());
            return;
        }

        Schedule schedule = Schedule.create(
                request.scheduleId(), request.startTime(), request.endTime(),
                request.ticketingTime(), request.title(), request.cookie(),
                request.creatorId(), request.movieId(), request.imageUrl(), request.seats()
        );
        scheduleRepository.save(schedule);

        eventPublisher.publishEvent(new ScheduleInitializedEvent(
                request.scheduleId(), request.ticketingTime(), request.startTime(), request.endTime()));
    } catch (Exception e) {
        log.error("movie.schedule.confirmed 처리 실패 - message={}", message, e);
        // 정상 소비 처리하여 Kafka 재시도 무한 루프 방지
    }
}
```

**주목할 점**:
- **`existsById` 멱등 체크** — Kafka의 at-least-once 전달 특성을 방어. 중복 수신 시 무시.
- **`try/catch` 그리고 "정상 소비"** — 예외를 밖으로 던지지 **않음**. 이유: 던지면 Kafka가 재시도 → 무한 루프. 대신 로그 남기고 다음 메시지로.
- **트랜잭션 + ApplicationEvent** 조합: Schedule 저장 트랜잭션 커밋 후 `ScheduleEventListener.handleScheduleInitialized`가 AFTER_COMMIT에서 Quartz Job 등록.

---

## 5. ★ QueueDrainConsumer — 동시성의 핵심

`infrastructure/messaging/consumer/QueueDrainConsumer.java:23-46`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueDrainConsumer {

    private final QueueAutoProcessService queueAutoProcessService;
    private final KafkaMessageUtil kafkaMessageUtil;

    @KafkaListener(
            topics = KafkaTopics.QUEUE_DRAIN,
            groupId = "queue-drain-group",
            concurrency = "4"
    )
    public void consume(String message) {
        try {
            QueueDrainMessage msg = kafkaMessageUtil.deserialize(message, QueueDrainMessage.class);
            queueAutoProcessService.checkAndProcess(msg.scheduleId());
        } catch (Exception e) {
            log.error("queue.drain 처리 실패 - message={}", message, e);
            // 정상 소비 처리하여 Kafka 재시도 무한 루프 방지
            // 다음 queue.drain 메시지에서 재트리거됨
        }
    }
}
```

### ★ concurrency=4 + key=scheduleId의 의미

- **파티션 수 = 4** (관례상 consumer concurrency와 맞춤).
- 프로듀서가 `send(topic, key=scheduleId, payload)` → 같은 scheduleId의 메시지는 **항상 같은 파티션**에 감.
- 같은 파티션은 **하나의 컨슈머 스레드**가 처리 → 같은 scheduleId에 대한 `checkAndProcess`는 **동시에 하나만** 실행.
- 서로 다른 scheduleId는 서로 다른 파티션 가능 → **최대 4개 스케줄을 병렬로 처리**.

### 만약 concurrency=1이라면?

→ 모든 스케줄의 드레인이 한 스레드에서 순차 처리. 스케줄 A의 긴 드레인이 스케줄 B의 급한 처리를 블록.

### 만약 concurrency=16이라면?

→ 파티션이 4개인데 컨슈머가 16 → 12개는 idle. 리소스 낭비.

### 왜 catch 후 "정상 소비"?

- 예외 시 재소비하면 같은 메시지 무한 반복 → 진짜 문제는 덮이고 시스템만 바쁨.
- 대안: **재고가 변할 때마다 새 `queue.drain`이 발행됨** (환불·결제 완료 시) → 자연 재시도.
- 복구 불가능 오류(UUID 파싱 실패 등)는 수동 조사 대상.

---

## 6. queueExecutor — 병렬 실행 스레드풀

`infrastructure/config/AsyncConfig.java:20-32`

```java
@Bean("queueExecutor")
public Executor queueExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("queue-drain-worker-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
}
```

### 왜 core=4, max=8?

- `QueueDrainConsumer` concurrency=4 — 드레인이 최대 4개 동시.
- 각 드레인의 윈도우 크기가 커지면 동시 tryPurchase가 여럿 → core=4로는 부족할 수 있음 → max=8로 여유.

### ★ CallerRunsPolicy의 의미

`queueCapacity=50`을 초과하고 스레드도 max=8까지 찬 상태라면:
- **기본 정책(AbortPolicy)**: `RejectedExecutionException` 발생.
- **CallerRunsPolicy**: **제출한 스레드가 직접 실행** → 제출자가 block → Kafka 컨슈머 스레드가 멈춤 → **자연 백프레셔**.

결과: 컨슈머는 "처리 완료되기 전에는 다음 메시지 안 가져감" → Kafka lag으로 문제가 가시화 + 시스템 과부하 방지.

### `@Async` 폐기 이력

과거에는 `checkAndProcess`를 `@Async`로 비동기 호출했음. 문제:
- 스케줄 수 × 이벤트 수가 늘어나면 **commonPool 고갈**.
- Kafka 컨슈머는 계속 바쁘게 돌지만 실제 처리는 밀림 → 관측 어려움.

해결:
- Kafka 토픽을 **"실행 큐"로 활용** → `queue.drain` 메시지가 "나중에 이 스케줄의 드레인을 해달라".
- `key=scheduleId` 파티셔닝으로 **순차 처리 보장**.
- 전용 `queueExecutor`로 윈도우 내부 병렬 제한.

→ 결과: 리소스 사용이 명시적·제어가능해짐.

---

## 7. `DailyTicketFeeProvideScheduler` — 1AM 배치 트리거

`infrastructure/scheduling/DailyTicketFeeProvideScheduler.java`는 Spring의 `@Scheduled(cron = "0 0 1 * * *")`로 `ProvideTicketFeeService.provide()` 호출. (단건 Cron이라 Quartz 아님.)

- Stage 6의 Spring Batch가 실제 일을 함.
- 이 스케줄러는 트리거만 — 6줄짜리 바디.

---

## 8. 설계 교훈 3가지

### (1) 시간 기반은 Quartz, 이벤트 기반은 Kafka — 각자의 강점
- Quartz: "언제"에 특화. DB 영속성·클러스터 분산·`@DisallowConcurrentExecution`.
- Kafka: "누가 알려주면"에 특화. 파티션 키로 동시성 단위 설정.

### (2) Kafka key 파티셔닝 = 자연스러운 mutual exclusion
- 별도 락 없이 "같은 scheduleId의 드레인은 하나만".
- 분산 락(Redlock, ZooKeeper)을 피한 가장 저렴한 방법.

### (3) 컨슈머 예외는 "정상 소비 + 로그"가 기본
- Kafka 재시도 루프는 위험. 로그 알람 → 수동 개입이 안전망.
- 단, 재시도가 의미 있는 경우엔 DLQ 구성을 고려 (현재 없음).

---

## ★ 핵심 질문

1. ★ `QueueDrainConsumer` concurrency를 **1로 줄이면** 어떤 문제가? **16으로 늘리면**?
2. ★ `CallerRunsPolicy`가 발동되는 정확한 조건은? 발동되면 Kafka 컨슈머 스레드는 무엇을 하고 있는가?
3. ★ `TicketingStartQuartzJob`에 `@DisallowConcurrentExecution`이 없다면 어떤 버그가 가능한가? `TicketingStartService.execute`가 두 스레드에서 동시 호출되는 시나리오를 상상해 보라.
4. `ScheduleEventConsumer`와 `QueueDrainConsumer`가 둘 다 예외를 **catch해 로그만 남기는** 이유는? 예외를 re-throw하면 어떻게 되는가?
5. 과거 `@Async` 패턴이 왜 폐기됐나? "스레드풀 고갈"을 코드 레벨로 설명해 보라.
6. prod 환경에서 두 개의 서버 인스턴스가 기동될 때 같은 `TicketingStartQuartzJob_42`가 **두 번 실행될 가능성**은? (힌트: Quartz clustered mode, JobStore)

---

## 체크리스트

- [ ] Quartz Job 5종이 호출하는 UseCase 5종을 짝지을 수 있다
- [ ] `@DisallowConcurrentExecution`의 보호 범위(같은 JobKey)를 Kafka key 파티셔닝의 보호 범위(같은 key)와 구분해 설명할 수 있다
- [ ] `QueueDrainConsumer` 의 `concurrency="4"` + `key=scheduleId` 조합이 어떻게 "스케줄 단위 mutual exclusion + 스케줄 간 병렬"을 만드는지 설명할 수 있다
- [ ] `queueExecutor`의 `CallerRunsPolicy`가 왜 "자연 백프레셔"인지 설명할 수 있다
- [ ] Kafka consumer의 catch/log 패턴이 재시도 루프를 막는 이유를 안다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/infrastructure/scheduling/*QuartzJob.java` (5개)
- `src/main/java/com/example/ticketservice/infrastructure/scheduling/DailyTicketFeeProvideScheduler.java`
- `src/main/java/com/example/ticketservice/infrastructure/messaging/consumer/QueueDrainConsumer.java`
- `src/main/java/com/example/ticketservice/infrastructure/messaging/consumer/ScheduleEventConsumer.java`
- `src/main/java/com/example/ticketservice/infrastructure/config/AsyncConfig.java`
- `docs/reference/infra/02-quartz-job-chaining.md` — Job 체이닝 공식 설명
- `docs/troubleshooting/01-resource-and-thread-management.md` — `@Async` 폐기 사례

← 이전: [Stage 4 — 이벤트 리스너](stage-04-event-listeners.md)
→ 다음: [Stage 6 — Spring Batch](stage-06-spring-batch.md)