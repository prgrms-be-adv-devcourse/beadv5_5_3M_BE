# Quartz Scheduler 사용 가이드

## @Scheduled vs Quartz 차이

| | @Scheduled | Quartz |
|-|-----------|--------|
| 실행 시점 | cron/fixedRate (고정 패턴) | 동적으로 지정 가능 |
| 파라미터 전달 | 불가 | JobDataMap으로 가능 |
| 클러스터 환경 | 모든 노드에서 중복 실행 | JDBC JobStore로 한 노드만 실행 |
| 서버 재시작 | 등록 정보 유실 | DB에 영속화, 재시작 후 복구 |
| 적합한 케이스 | 매일 1시 배치처럼 고정 주기 | 이벤트 발생 시 특정 시각에 1회 실행 |

이 프로젝트는 스케줄 확정 시점마다 다른 시각(ticketingTime - 24h)에 Job을 등록해야 하므로 Quartz를 사용한다.

---

## 의존성

```gradle
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-quartz'
```

Spring Boot가 `Scheduler` Bean을 자동 구성해준다.

---

## 설정

```yaml
# application-dev.yaml
spring:
  quartz:
    job-store-type: jdbc          # Job 정보를 DB에 저장 (메모리: memory)
    jdbc:
      initialize-schema: always   # Quartz 테이블 자동 생성
    properties:
      org.quartz.jobStore.isClustered: false                               # 단일 서버: false, 클러스터: true
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
      org.quartz.threadPool.threadCount: 5                                 # Job 실행 스레드 수
```

`initialize-schema: always`로 설정하면 앱 시작 시 Quartz 전용 테이블(11개)이 자동 생성된다.
이미 있으면 무시한다.

---

## 핵심 구성 요소

### JobDetail — "무엇을 실행할지"

```java
JobDetail job = JobBuilder.newJob(CartCloseQuartzJob.class)  // 실행할 Job 클래스
    .withIdentity("CartCloseJob_" + scheduleId, "CART_CLOSE") // (이름, 그룹) — 고유 식별자
    .usingJobData("scheduleId", scheduleId)                   // 파라미터 전달
    .storeDurably()                                           // 트리거 없어도 DB에 유지
    .build();
```

### Trigger — "언제 실행할지"

```java
Date startAt = Date.from(triggerTime.atZone(ZoneId.systemDefault()).toInstant());

Trigger trigger = TriggerBuilder.newTrigger()
    .withIdentity("CartCloseTrigger_" + scheduleId, "CART_CLOSE")
    .startAt(startAt)                                         // 지정 시각 1회 실행
    .withSchedule(SimpleScheduleBuilder.simpleSchedule())     // 반복 없음 (1회)
    .build();
```

반복 실행이 필요하면:
```java
// 10초마다 5번 반복
.withSchedule(SimpleScheduleBuilder.simpleSchedule()
    .withIntervalInSeconds(10)
    .withRepeatCount(5))

// cron 표현식
.withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 * * ?"))
```

### Job 등록

```java
scheduler.scheduleJob(job, trigger);  // JobDetail + Trigger 동시 등록
```

### Job 취소

```java
scheduler.deleteJob(new JobKey("CartCloseJob_" + scheduleId, "CART_CLOSE"));
```

---

## Job 클래스 작성법

Spring Bean 의존성 주입이 되려면 `QuartzJobBean`을 상속해야 한다.

```java
@Slf4j
@DisallowConcurrentExecution   // ← 같은 Job이 동시에 2개 실행되는 것 방지 (클러스터에서 중요)
public class CartCloseQuartzJob extends QuartzJobBean {

    public static final String SCHEDULE_ID_KEY = "scheduleId";

    // ① setter 주입 — Quartz가 내부적으로 setter를 호출해 Bean을 주입
    private CartCloseUseCase cartCloseUseCase;

    public void setCartCloseUseCase(CartCloseUseCase cartCloseUseCase) {
        this.cartCloseUseCase = cartCloseUseCase;
    }

    // ② 실행 메서드
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        // ③ JobDataMap에서 파라미터 꺼내기
        Long scheduleId = context.getJobDetail().getJobDataMap().getLong(SCHEDULE_ID_KEY);

        try {
            cartCloseUseCase.execute(scheduleId);
        } catch (Exception e) {
            // ④ 예외를 JobExecutionException으로 감싸서 throw → Quartz가 실패 처리
            throw new JobExecutionException(e);
        }
    }
}
```

**주의:** `@Component` 없이 `QuartzJobBean` 상속만 하면 된다.
`@Component`를 붙이면 Spring이 직접 Bean으로 관리하려 해서 Quartz와 충돌할 수 있다.

---

## 파라미터 전달 — JobDataMap

Job 등록 시 데이터를 넣고:
```java
.usingJobData("scheduleId", scheduleId)   // Long
.usingJobData("userId", userId.toString()) // String
.usingJobData("retryCount", 3)            // int
```

Job 실행 시 꺼낸다:
```java
JobDataMap dataMap = context.getJobDetail().getJobDataMap();
Long scheduleId = dataMap.getLong("scheduleId");
String userId   = dataMap.getString("userId");
int retryCount  = dataMap.getInt("retryCount");
```

---

## 이 프로젝트의 Job 그룹 구조

```
CART_CLOSE 그룹
  └─ CartCloseJob_{scheduleId}   → CartCloseTrigger_{scheduleId}

TICKETING_START 그룹
  └─ TicketingStartJob_{scheduleId} → TicketingStartTrigger_{scheduleId}

REVIEW_AUTH 그룹
  └─ ReviewAuthJob_{scheduleId}  → ReviewAuthTrigger_{scheduleId}
```

그룹을 쓰는 이유: 취소 시 그룹 단위 조회가 가능하고, 로그/모니터링에서 구분하기 쉽다.

---

## Quartz가 생성하는 DB 테이블

`initialize-schema: always`로 자동 생성되는 주요 테이블:

| 테이블 | 역할 |
|--------|------|
| `QRTZ_JOB_DETAILS` | 등록된 JobDetail 목록 |
| `QRTZ_TRIGGERS` | 등록된 Trigger 목록 |
| `QRTZ_SIMPLE_TRIGGERS` | SimpleSchedule Trigger 상세 |
| `QRTZ_CRON_TRIGGERS` | CronSchedule Trigger 상세 |
| `QRTZ_FIRED_TRIGGERS` | 현재 실행 중인 Job 목록 |
| `QRTZ_LOCKS` | 클러스터 분산 락 |

등록된 Job 확인:
```sql
SELECT job_name, job_group, job_class_name
FROM qrtz_job_details;

SELECT trigger_name, trigger_group, next_fire_time, trigger_state
FROM qrtz_triggers;
-- next_fire_time: Unix timestamp (ms) → to_timestamp(next_fire_time/1000)으로 변환
```

---

## @Scheduled과 함께 쓸 때

이 프로젝트처럼 `@Scheduled`(배치)와 Quartz를 같이 쓰는 경우 충돌 없다.
`@EnableScheduling`과 `@EnableAsync`가 설정되어 있으면 둘 다 동작한다.

```java
// @Scheduled: 고정 주기 (매일 1시 배치)
@Scheduled(cron = "0 0 1 * * *")
public void run() { ... }

// Quartz: 동적 시각 (스케줄별로 다른 ticketingTime)
scheduler.scheduleJob(job, trigger);
```

---

## 자주 겪는 문제

### Job이 실행되지 않을 때

```sql
-- trigger_state 확인
SELECT trigger_name, trigger_state, next_fire_time
FROM qrtz_triggers;

-- PAUSED: 일시 정지 상태
-- WAITING: 실행 대기 중 (정상)
-- COMPLETE: 이미 실행 완료
-- ERROR: 실행 실패
```

### 서버 재시작 후 중복 등록 오류

`scheduleJob()` 호출 시 같은 `JobKey`가 이미 존재하면 예외 발생.
이미 등록된 경우를 처리하려면:

```java
if (scheduler.checkExists(job.getKey())) {
    scheduler.deleteJob(job.getKey());
}
scheduler.scheduleJob(job, trigger);
```

또는 `rescheduleJob()`으로 기존 트리거를 교체:

```java
scheduler.rescheduleJob(trigger.getKey(), trigger);
```

### 과거 시각으로 트리거 등록

`startAt()`에 과거 시각을 넣으면 앱 시작 직후 즉시 실행된다.
테스트할 때 의도치 않게 Job이 바로 실행될 수 있으니 주의.