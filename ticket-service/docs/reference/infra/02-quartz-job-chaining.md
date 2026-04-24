# Quartz Job 체이닝

## 개요

스케줄 하나당 5개의 Quartz Job이 등록된다.
각 Job은 독립적으로 트리거되지만 실행 순서가 보장되어 있고,
앞 단계의 상태 전이(`schedule.status`)를 뒷 단계가 검증한다.

---

## Job 등록 흐름

```
ScheduleEventConsumer (Kafka consumer)
  → Schedule 저장 (status: CART)
  → ScheduleInitializedEvent 발행

    │ @TransactionalEventListener(AFTER_COMMIT)
    ▼
ScheduleEventListener.handleScheduleInitialized()
  │
  ├─ schedulerPort.scheduleCartCloseJob(scheduleId, ticketingTime.minusHours(24))
  ├─ schedulerPort.scheduleTicketingStartJob(scheduleId, ticketingTime)
  ├─ schedulerPort.scheduleReviewAuthJob(scheduleId, startTime)
  ├─ schedulerPort.scheduleStreamingStartJob(scheduleId, startTime)
  └─ schedulerPort.scheduleStreamingFinishJob(scheduleId, endTime)
```

DB 커밋 후 `@TransactionalEventListener(AFTER_COMMIT)`에서 등록하므로
Schedule이 DB에 저장된 것이 보장된 상태에서 Job이 등록된다.

---

## Job 상세

### Job 1: CartCloseQuartzJob

```
트리거:  ticketingTime - 24h
그룹:    CART_CLOSE
JobData: scheduleId

실행:
  CartCloseUseCase.execute(scheduleId)
    → 수요 vs 재고 비교 (Redis cart count)
    → Case A: RESERVED 티켓 bulk 생성
    → Case B: 가예약 없음
    → schedule.closeCart() → CART → IN_PROGRESSING
    → CartClosedEvent 발행 (AFTER_COMMIT → Kafka cart.closed)
```

### Job 2: TicketingStartQuartzJob

```
트리거:  ticketingTime
그룹:    TICKETING_START
JobData: scheduleId

실행:
  TicketingStartUseCase.execute(scheduleId)
    → 미결제 RESERVED 티켓 일괄 DELETE
    → remaining = seats - count(CONFIRMED)
    → Redis stock 초기화 (TTL: startTime - 10min)
    → schedule.startTicketing() → IN_PROGRESSING → TICKETING
    → TicketingStartedEvent 발행 (AFTER_COMMIT → Kafka ticketing.started)
```

### Job 3: ReviewAuthQuartzJob

```
트리거:  startTime - 10m (streaming-service LOBBY_OPEN 시점)
그룹:    REVIEW_AUTH
JobData: scheduleId

실행:
  ReviewAuthUseCase.publishReviewAuth(scheduleId)
    → DB에서 CONFIRMED 티켓 목록 조회
    → 각 userId에 Kafka ticket.review.authorized 발행
```

**트리거 시각 근거**: streaming-service 가 `startTime - 10m` 에 대기실(LOBBY)을 개방하고
이 시점부터 WebSocket CONNECT · 세션 발급이 가능하다. Entitlement 사본이 그 순간
이미 streaming-service 쪽에 적재되어 있어야 `NO_ENTITLEMENT` 오류 없이 권한 검증이 통과됨.

### Job 4: StreamingStartQuartzJob

```
트리거:  startTime (공연 시작)
그룹:    STREAMING_START
JobData: scheduleId

실행:
  StreamingStartUseCase.execute(scheduleId)
    → schedule.startStreaming() → TICKETING → STREAMING
```

### Job 5: StreamingFinishQuartzJob

```
트리거:  endTime (공연 종료)
그룹:    STREAMING_FINISH
JobData: scheduleId

실행:
  StreamingFinishUseCase.execute(scheduleId)
    → schedule.finishStreaming() → STREAMING → FINISH
```

---

## Job 타임라인 시각화

```
스케줄 확정
    │
    │◄──────── CART 기간 (유저가 장바구니 담기) ────────►│
    │                                                    │
    T                                           T - 24h  │
    │                                                    │
    │                                            [Job 1: CartCloseJob]
    │                                            Case A/B 분기
    │                                            → IN_PROGRESSING
    │
    │◄── IN_PROGRESSING (24h 유예기간: Case A 자율결제) ──►│
    │                                                     │
    │                                             ticketingTime
    │                                                     │
    │                                          [Job 2: TicketingStartJob]
    │                                          stock 초기화
    │                                          → TICKETING
    │
    │◄──────── TICKETING 기간 (대기열/즉시구매) ──────────►│
    │                                                     │
    │                                         startTime - 10min
    │                                         (stock/queue TTL 만료 → 티켓팅 마감)
    │
    │                                              startTime
    │                                                 │
    │                                   [Job 3: ReviewAuthJob]
    │                                   CONFIRMED 티켓 → 리뷰 권한 발행
    │                                   [Job 4: StreamingStartJob]
    │                                   → STREAMING
    │
    │◄──────────────── STREAMING 기간 ───────────────────►│
    │                                                     │
    │                                               endTime
    │                                                 │
    │                                   [Job 5: StreamingFinishJob]
    │                                   → FINISH
    │
    endTime
```

---

## 구현 세부사항

### QuartzSchedulerAdapter

```java
// 예: CartCloseJob 등록
JobDetail job = JobBuilder.newJob(CartCloseQuartzJob.class)
    .withIdentity("cartClose-" + scheduleId, "CART_CLOSE")
    .usingJobData("scheduleId", scheduleId)
    .storeDurably()
    .build();

Trigger trigger = TriggerBuilder.newTrigger()
    .startAt(Date.from(triggerTime.atZone(ZoneId.systemDefault()).toInstant()))
    .build();

scheduler.scheduleJob(job, trigger);
```

### @DisallowConcurrentExecution

모든 Job에 적용. 클러스터 환경에서 동일 Job이 여러 노드에서 중복 실행되는 것을 방지.

```java
@DisallowConcurrentExecution
public class CartCloseQuartzJob implements Job { ... }
```

### JDBC JobStore

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always
    properties:
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
```

Job 정보를 DB에 영속화하여 서버 재시작 시에도 예약된 Job이 유지된다.

### Job 취소

스케줄이 취소되는 경우 `schedulerPort.cancelScheduledJobs(scheduleId)` 호출:

```java
// 5개 그룹의 Job을 모두 삭제
scheduler.deleteJob(JobKey.jobKey("cartClose-" + scheduleId, "CART_CLOSE"));
scheduler.deleteJob(JobKey.jobKey("ticketingStart-" + scheduleId, "TICKETING_START"));
scheduler.deleteJob(JobKey.jobKey("reviewAuth-" + scheduleId, "REVIEW_AUTH"));
scheduler.deleteJob(JobKey.jobKey("streamingStart-" + scheduleId, "STREAMING_START"));
scheduler.deleteJob(JobKey.jobKey("streamingFinish-" + scheduleId, "STREAMING_FINISH"));
```

---

## 상태 검증 체인

각 Job은 실행 전 앞 단계의 상태를 검증하여 중복/순서 오류를 방지한다.

| Job | 진입 조건 | 전환 후 |
|-----|----------|--------|
| CartCloseJob | `schedule.status == CART` | `IN_PROGRESSING` |
| TicketingStartJob | `schedule.status == IN_PROGRESSING` | `TICKETING` |
| StreamingStartJob | (상태 검증 없음, 공연 시작 시점) | `STREAMING` |
| StreamingFinishJob | (상태 검증 없음, 공연 종료 시점) | `FINISH` |
| ReviewAuthJob | (상태 검증 없음, 공연 시작 시점) | 변경 없음 |

상태가 맞지 않으면 예외(`ScheduleErrorCode`)를 던지고 Job 실행을 중단한다.