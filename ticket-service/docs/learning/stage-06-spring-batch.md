# Stage 6 — Spring Batch (일일 대금 지급 · 미결제 회수)

> **목표**: 두 개의 서로 다른 배치 패턴을 이해한다.
> (1) 스케줄러에서 돌리는 **대량·페이지 기반** Tasklet (ticketProvideJob)
> (2) Quartz Job에서 **동기 호출**하는 작은 트랜잭션 배치 (TicketCleanupBatch)
> 왜 배치로 처리하고 실시간으로 안 했는지, 배치 실패가 어디까지 전파되는지를 본다.
> **예상 소요**: 0.5일

---

## 1. 두 배치의 비교

| 항목 | `ticketProvideJob` (일일 대금 지급) | `TicketCleanupBatch` (미결제 회수) |
|------|----------------------------------|-----------------------------------|
| 트리거 | `@Scheduled(cron="0 0 1 * * *")` (매일 1 AM) | `TicketingStartService.execute()`에서 동기 호출 |
| 처리 방식 | Paged Tasklet (pageSize=500), loop | 단일 DELETE 쿼리 한 번 |
| Spring Batch 구성요소 | Job + Step + Tasklet, JobOperator | 없음 (단순 `@Transactional` + 벌크 DELETE) |
| 작업량 | 전날 구매된 CONFIRMED 티켓 전체 | 한 스케줄의 RESERVED 티켓 |
| 실패 시 전파 | `JobExecution.getStatus()` 확인 → 로그만 | `RuntimeException` → 호출자(TICKETING 전이) 롤백 |

**주목할 점**: 이름에 `Batch`가 붙었다고 둘 다 Spring Batch 풀세트를 쓰는 게 아님. `TicketCleanupBatchAdapter`는 포트를 구현했을 뿐 **Job/Step 없이** 단일 트랜잭션 벌크 DELETE. 본격 배치는 `TicketProvideBatchConfig` 하나.

---

## 2. ticketProvideJob — Paged Tasklet

### 2.1 트리거 — `@Scheduled` cron

`infrastructure/scheduling/DailyTicketFeeProvideScheduler.java` (18줄)

```java
@Component
@RequiredArgsConstructor
public class DailyTicketFeeProvideScheduler {

    private final ProvideTicketFeeUseCase provideTicketFeeUseCase;

    @Scheduled(cron = "0 0 1 * * *") // 매일 오전 1시
    public void run() {
        provideTicketFeeUseCase.provide();
    }
}
```

**왜 Quartz가 아니고 `@Scheduled`인가?**
- Quartz는 **스케줄별(scheduleId마다) Job**을 동적 등록해야 하는 용도 (Stage 5 참고).
- 일일 배치는 **고정 cron**이면 충분 → Spring의 경량 `@Scheduled`로 충분.
- 단, 멀티 인스턴스에서는 중복 실행 위험 (`docs/troubleshooting/05-scheduling-and-batch-safety.md` SCH-002 참고) → 현재는 단일 인스턴스 운영 전제.

### 2.2 포트 어댑터 — `TicketProvideBatchAdapter`

`infrastructure/batch/TicketProvideBatchAdapter.java` (45줄)

```java
@Component
public class TicketProvideBatchAdapter implements TicketProvideBatchPort {

    private final JobOperator jobOperator;
    private final Job ticketProvideJob;

    @Override
    public boolean run() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())   // ★ 실행마다 다른 파라미터
                    .toJobParameters();

            JobExecution execution = jobOperator.start(ticketProvideJob, params);
            log.info("일일 대금 지급 배치 완료 - status: {}", execution.getStatus());
            return execution.getStatus() == BatchStatus.COMPLETED;
        } catch (Exception e) {
            log.error("일일 대금 지급 배치 실행 중 예외 발생", e);
            return false;
        }
    }
}
```

**주목할 점**:
- **`runAt = LocalDateTime.now()` JobParameter가 매 실행마다 다름** → Spring Batch는 파라미터가 같은 Job 인스턴스는 재실행 거부. `runAt`이 매번 다르므로 "같은 날 여러 번 돌리기"도 이론상 가능 (현재는 하루 1회).
- `jobOperator.start()`는 **동기** 호출 → `@Scheduled` 스레드가 배치 완료까지 block.
- 예외를 잡아 `return false` — 호출자(`ProvideTicketFeeService`)는 이 결과를 무시 → **실패해도 시스템은 계속 돌아감**. 다음날 1 AM에 다시 시도.

### 2.3 Job 정의 — `TicketProvideBatchConfig`

`infrastructure/batch/TicketProvideBatchConfig.java` (107줄)

```java
@Configuration
public class TicketProvideBatchConfig {

    private static final int PAGE_SIZE = 500;

    private final TicketJpaRepository ticketJpaRepository;
    private final EventPublisherPort eventPublisherPort;  // @Qualifier("kafkaBulkEventPublisher")

    @Bean
    public Step ticketProvideStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder("ticketProvideStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    int totalUpdated = 0, totalPublished = 0, page = 0;

                    Slice<Ticket> slice;
                    do {
                        // ① Slice 조회 — provideFlag=false만 보므로 항상 page=0 유지 OK
                        slice = ticketJpaRepository.findByStatusAndProvideFlag(
                                TicketStatus.CONFIRMED, false, PageRequest.of(0, PAGE_SIZE));

                        List<Ticket> tickets = slice.getContent();
                        if (tickets.isEmpty()) break;

                        // ② Kafka 메시지 사전 수집 (schedule은 LAZY → 이 시점에 load)
                        List<DailyTicketFeeProvideMessage> messages = tickets.stream()
                                .map(t -> new DailyTicketFeeProvideMessage(
                                        t.getSchedule().getCreatorId(),
                                        t.getId(),
                                        t.getSchedule().getId(),
                                        t.getSchedule().getCookie()))
                                .toList();

                        // ③ DB 먼저: provideFlag = true 벌크 UPDATE
                        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
                        int updated = ticketJpaRepository.bulkMarkProvided(ticketIds);

                        // ④ Kafka 발행 (bulk publisher — linger.ms + snappy 적용)
                        messages.forEach(msg ->
                                eventPublisherPort.publish(KafkaTopics.TICKET_PROVIDE,
                                        msg.ticketId().toString(), msg));

                        totalUpdated += updated;
                        totalPublished += messages.size();
                        page++;
                    } while (slice.hasNext());

                    log.info("...페이지: {}, DB 업데이트: {}, Kafka 발행: {}",
                            page, totalUpdated, totalPublished);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
```

### 2.4 ★ 왜 `PageRequest.of(0, PAGE_SIZE)` offset이 항상 0인가?

일반적인 Paging 루프는 `PageRequest.of(page++, size)`로 offset을 늘린다. 여기선 **항상 0**.
→ 이유: **조회 조건 `provideFlag = false`가 루프 안에서 true로 바뀌기 때문**.
- 페이지 1 조회 → 500건 UPDATE → 이제 `false`인 티켓은 **원래의 501번째부터**가 됨.
- 그 다음에 또 `offset=0, size=500`을 조회하면 원래의 501~1000번째가 나옴.
- offset을 늘렸다면 "이미 처리한 500건을 스킵" + "다음 500건 가져오기"인데, 우리 케이스에선 **처리한 500건이 조회 조건에서 사라졌으므로** offset=0이 자연스럽게 다음 500건을 가리킴.

**trap**: 만약 업데이트 후에 commit 없이 이어서 돌리면 영속성 컨텍스트 때문에 잘못된 결과가 나올 수 있음 → `@Modifying(clearAutomatically = true)`가 1차 캐시 초기화를 담당 (Ticket.java의 JpaRepository 정의).

### 2.5 ★ 왜 DB 먼저 UPDATE → Kafka 발행인가?

순서가 반대면:
- 먼저 Kafka 발행 → 정산 서비스가 받아 처리 → 여기서 DB UPDATE 실패
- → 정산은 되었는데 `provideFlag=false` 상태 유지 → **다음날 또 Kafka 발행** → 중복 정산

`provideFlag=true` 커밋 후 Kafka 발행 중 실패하면:
- 정산 서비스는 이벤트 못 받음 → 크리에이터 **대금 미지급** → 수동 복구 필요 (덜 나쁜 실패 모드).
- 단, Kafka 프로듀서는 fire-and-forget + DLQ 없음 → 로그로만 감지 가능.

**설계 철학**: 중복보다 누락이 덜 심각 (누락은 장부로 식별 가능, 중복은 환급 절차가 복잡).

### 2.6 Kafka 페이로드

`DailyTicketFeeProvideMessage` (record, 13줄)

```java
public record DailyTicketFeeProvideMessage(
        UUID creatorId,
        Long ticketId,
        Long scheduleId,
        Integer cookieAmount
) {}
```

→ `topic: ticket.provide`, **receiver: settlement-service**. 이 메시지의 누적으로 크리에이터별 쿠키 잔액이 정산 DB에 기록됨.

---

## 3. TicketCleanupBatch — "이름뿐인" 배치

### 3.1 어댑터 구현

`infrastructure/batch/TicketCleanupBatchAdapter.java` (24줄)

```java
@Component
@RequiredArgsConstructor
public class TicketCleanupBatchAdapter implements TicketCleanupBatchPort {

    private final TicketRepository ticketRepository;

    @Transactional
    @Override
    public void run(Long scheduleId) {
        int deleted = ticketRepository.deleteAllByScheduleIdAndStatus(scheduleId, TicketStatus.RESERVED);
        log.info("미결제 RESERVED 티켓 일괄 삭제 완료 - scheduleId={}, deleted={}", scheduleId, deleted);
    }
}
```

**주목할 점**:
- **Spring Batch Job/Step 없음** — 단일 `@Transactional` + JPQL `DELETE` 한 번.
- 그런데 왜 `Batch`라는 이름의 포트를 썼나? → **"대량 삭제는 배치 성격"** 이라는 의미 전달 + 향후 chunk 처리로 교체 가능성 열어둠.
- 호출자(`TicketingStartService`)는 이 구현이 Spring Batch인지 단순 쿼리인지 몰라도 됨 (포트 추상화).

### 3.2 호출자의 실패 처리 — `TicketingStartService`

`application/service/TicketingStartService.java` (55줄 전체)

```java
@Transactional
public void execute(Long scheduleId) {
    Schedule schedule = ...;

    // ★ 배치 실패 → RuntimeException → TICKETING 전이 자체 롤백
    try {
        ticketCleanupBatchPort.run(scheduleId);
    } catch (Exception e) {
        throw new RuntimeException("미결제 RESERVED 티켓 삭제 실패 - scheduleId=" + scheduleId
                + ", TICKETING 전이를 중단합니다.", e);
    }

    long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, CONFIRMED);
    long remaining = schedule.getSeats() - confirmedCount;

    schedule.startTicketing();
    eventPublisher.publishEvent(new TicketingStartedEvent(scheduleId, remaining, ...));
}
```

**주목할 점** (SCH-003 해결 후):
- 배치 실패 시 **RuntimeException을 재던져** TICKETING 전이 트랜잭션을 통째로 롤백.
- 왜 중요? → cleanup이 실패하면 RESERVED 티켓이 남아있고 `countByScheduleIdAndStatus(CONFIRMED)`로 계산한 `remaining`이 실제보다 크게 나옴. Redis에 `stock`이 **과다 설정**되어 **오버부킹**.
- 롤백되면 Schedule은 IN_PROGRESSING으로 유지 → Quartz misfire가 다시 시도.

### 3.3 ★ 왜 TicketCleanup은 동기 실행인가?

- TICKETING 전이는 **원자 연산**이어야 함: ① 미결제 삭제 → ② 재고 계산 → ③ 상태 전이.
- 비동기로 돌리면 ②·③이 ①보다 먼저 실행될 수 있음 → remaining 계산 오류.
- Quartz `TicketingStartQuartzJob` 스레드는 배치 완료까지 block되지만, 이는 **의도된 설계** (순서 보장).
- 단, 대량 스케줄 동시 진입 시 Quartz 풀(dev=5, prod=10) 점유 주의 — 티켓팅 시작 시각이 동시에 겹치는 스케줄은 드물다는 가정.

---

## 4. 일일 배치가 꼭 필요한가? — 실시간화하면?

### 현재 배치 설계
- 구매 시점: `provideFlag = false` (기본값)
- 다음날 1 AM: 전체 CONFIRMED 티켓에 대해 `provideFlag = true` + settlement-service에 이벤트.

### "실시간화" 시나리오 가정

Self-payment 커밋 직후 `handleTicketPaid` 리스너가 **즉시 `ticket.provide`를 발행**한다면?

| 영향 | 실시간 | 배치 (현재) |
|------|--------|-------------|
| settlement-service 부하 | 매 구매마다 메시지 → 스파이크 심함 | 하루 1회 대량 전송 (snappy 압축) |
| 크리에이터 대시보드 실시간성 | ○ | 최대 1일 지연 |
| 환불 처리 복잡도 | 환불 시 이미 정산 반영됨 → 반대 정산 이벤트 필요 | 환불은 `ticket.refunded`만 발행, 정산은 **다음날 배치 시점에 이미 삭제된 티켓을 제외** → 자연스럽게 누락 |
| 실패 재시도 | 이벤트 1건 재전송 | 다음날 또 시도 |

**비즈니스 결정**: "크리에이터 정산은 1일 지연 허용" → 배치가 유리. 실시간 요구사항이 생기면 아키텍처 근본 변경 필요.

---

## 5. chunk size / PAGE_SIZE 트레이드오프

현재 `PAGE_SIZE = 500`. 만약 50 또는 5000으로 바꾸면?

| PAGE_SIZE | 한 루프 DB UPDATE | 한 루프 Kafka 발행 수 | 메모리 | 실패 재처리 비용 |
|-----------|-----------------|----------------------|-------|----------------|
| 50 | 적음, 락 시간 짧음 | 적음 | 낮음 | 50건 손실 (낮음) |
| 500 (현재) | 중간 | 중간 | 중간 | 500건 손실 |
| 5000 | 한 번에 락 길게 걺 | Kafka 배치 포화 | 높음 | 대량 손실 + 재처리 어려움 |

**주의**: 현재 Tasklet은 한 덩어리가 **단일 트랜잭션**. 즉 PAGE_SIZE=500이지만 loop 전체가 하나의 트랜잭션 아님 — `do-while` 각 iteration이 bulk UPDATE를 commit하지 않고 계속 누적? → JPA `@Modifying`은 현재 트랜잭션 안에서 즉시 flush. Tasklet은 한 번 호출되고 끝이므로 **전체 루프가 하나의 트랜잭션**. 실패 시 **전체 롤백**.

---

## 6. 이 Stage의 "설계 교훈"

1. **"배치 = 일관성 경계"**: 한 Tasklet = 한 트랜잭션. 실패 시 전체 롤백. 멱등 연산(`provideFlag=true`)만 넣어야 재시도 안전.
2. **"이름이 Batch라도 Spring Batch가 아닐 수 있다"**: 포트 이름만 보고 구현을 예단하지 말 것. 실제 코드를 봐야 함.
3. **배치 실패 처리의 두 패턴**: ① 로그만 남기고 다음날 재시도(ticketProvide) vs ② 호출자 트랜잭션 롤백(TicketCleanup). 어느 쪽이 적절한지는 **실패 시 부작용의 심각도**로 결정.

---

## ★ 핵심 질문

1. ★ `ticketProvideJob`의 `PageRequest.of(0, 500)`에서 offset이 항상 0인 이유는? offset을 `page++`로 늘린다면 어떤 버그?
2. ★ DB UPDATE를 Kafka 발행보다 **먼저** 하는 이유는? 반대 순서라면 어떤 장애?
3. `TicketCleanupBatch`는 실제로 Spring Batch Job을 쓰지 않는데, 왜 포트 이름에 `Batch`를 남겼는가?
4. `ticketProvideJob`은 실패해도 시스템이 계속 돌고, `TicketCleanupBatch`는 실패하면 호출자 트랜잭션까지 롤백. 두 설계 판단의 차이를 **실패 시 부작용**으로 설명하라.
5. `@Scheduled` + 단일 인스턴스로 운영 중인 이 배치를 멀티 인스턴스로 확장하면 어떤 문제? 해결 수단 2가지는? (힌트: SCH-002)

---

## 체크리스트

- [ ] `ticketProvideJob`의 Paged Tasklet 전체 루프를 설명할 수 있다 (조회 → 메시지 수집 → UPDATE → Kafka)
- [ ] `provideFlag=false` 조건 때문에 offset이 고정인 이유를 설명할 수 있다
- [ ] DB → Kafka 순서가 "누락 < 중복" 결정에서 나왔음을 이해했다
- [ ] `TicketCleanupBatch` 실패가 TICKETING 전이 자체를 롤백시키는 호출 체인을 안다
- [ ] 일일 배치를 실시간화했을 때의 부하·복잡도 트레이드오프를 설명할 수 있다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/infrastructure/batch/TicketProvideBatchConfig.java`
- `src/main/java/com/example/ticketservice/infrastructure/batch/TicketProvideBatchAdapter.java`
- `src/main/java/com/example/ticketservice/infrastructure/batch/TicketCleanupBatchAdapter.java`
- `src/main/java/com/example/ticketservice/infrastructure/scheduling/DailyTicketFeeProvideScheduler.java`
- `src/main/java/com/example/ticketservice/application/service/TicketingStartService.java` — cleanup 호출부
- `src/main/java/com/example/ticketservice/infrastructure/persistence/TicketJpaRepository.java` — `bulkMarkProvided`, `deleteAllByScheduleIdAndStatus`
- `src/main/java/com/example/ticketservice/infrastructure/messaging/dto/event/DailyTicketFeeProvideMessage.java`
- `docs/troubleshooting/05-scheduling-and-batch-safety.md` — SCH-001 ~ SCH-003

← 이전: [Stage 5 — Quartz Job + Kafka Consumer](stage-05-quartz-kafka.md)
→ 다음: [Stage 7 — 동시성 심화 (Queue & Stock)](stage-07-concurrency-deep.md)