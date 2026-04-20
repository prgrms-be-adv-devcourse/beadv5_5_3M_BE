# 스케줄링·배치 안전성 (SCH-001 ~ SCH-003)

> Quartz Job 멱등성, @Scheduled 중복 실행, 배치 실패 전파 관련 이슈와 해결 방법

---

## 핵심 원칙

Quartz Job은 misfire·재시도로 동일 Job이 재실행될 수 있다. 상태 전이 서비스는 반드시 **현재 상태를 확인하고 기대 상태가 아니면 early return** 해야 한다. 예외를 throw하면 Quartz가 FAILED로 기록 → 재시도 루프에 빠진다.

---

## SCH-001: Streaming Start/Finish 상태 가드 누락 [CRITICAL → RESOLVED]

**파일:** `application/service/StreamingStartService.java`, `application/service/StreamingFinishService.java`

### 현상

```java
// StreamingStartService
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(...);
    schedule.startStreaming();  // TICKETING이 아니면 도메인 예외 발생
    // → JobExecutionException → Quartz FAILED → misfire 재시도 → 무한 반복
}
```

Quartz가 misfire 정책에 의해 Job을 재실행하면, 이미 STREAMING 상태인 스케줄에서 `startStreaming()` 호출 시 도메인 예외 → Job 계속 실패.

### 해결 — 서비스 레이어 상태 가드

```java
// StreamingStartService
@Transactional
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    if (schedule.getStatus() != ScheduleStatus.TICKETING) {
        log.warn("스트리밍 시작 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
        return;  // 정상 완료 → Quartz COMPLETE → 재시도 없음
    }

    schedule.startStreaming();
    scheduleRepository.save(schedule);
}
```

```java
// StreamingFinishService
@Transactional
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    if (schedule.getStatus() != ScheduleStatus.STREAMING) {
        log.warn("스트리밍 종료 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
        return;
    }

    schedule.finishStreaming();
    scheduleRepository.save(schedule);
}
```

| 시나리오 | 변경 전 | 변경 후 |
|---------|---------|---------|
| 정상 실행 | TICKETING → STREAMING | 동일 |
| Quartz 재실행 (이미 STREAMING) | 도메인 예외 → FAILED → 재시도 루프 | early return → COMPLETE |

---

## SCH-002: @Scheduled 클러스터 환경 중복 실행 [CRITICAL → WONTFIX]

**파일:** `infrastructure/scheduling/DailyTicketFeeProvideScheduler.java`

### 현상

```java
@Scheduled(cron = "0 0 1 * * *")
public void run() {
    provideTicketFeeUseCase.provide();
}
```

멀티인스턴스 배포 시 모든 인스턴스에서 동시에 `@Scheduled` 실행 → 배치 중복 처리.

### 판단: 현재 스코프에서 수정하지 않음

- **단일 인스턴스 운영** 확정 → 중복 실행 불가
- 멀티인스턴스 전환 시 ShedLock 또는 Quartz Clustered 모드 도입 필요
- 현재 상황에서는 불필요한 의존성 추가를 지양

---

## SCH-003: ticketCleanupBatchPort.run() 실패 시 TICKETING 전이 진행 [MEDIUM → RESOLVED]

**파일:** `application/service/TicketingStartService.java`

### 현상

```java
@Transactional
public void execute(Long scheduleId) {
    // ...
    ticketCleanupBatchPort.run(scheduleId);  // 미결제 RESERVED 삭제 — 실패 시?
    long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, CONFIRMED);
    long remaining = schedule.getSeats() - confirmedCount;
    schedule.startTicketing();
    // cleanup 실패 → RESERVED 잔존 → remaining 계산 오류 → stock 부정확
}
```

미결제 RESERVED 티켓 삭제 배치가 실패해도 TICKETING 전이가 그대로 진행. `remaining` 값이 RESERVED 티켓만큼 부풀려져 실제보다 많은 재고가 Redis에 설정됨.

### 해결 — 배치 실패 시 전이 중단

```java
@Transactional
public void execute(Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    // 배치 실패 → RuntimeException → 트랜잭션 롤백 → TICKETING 전이 차단
    try {
        ticketCleanupBatchPort.run(scheduleId);
    } catch (Exception e) {
        throw new RuntimeException(
                "미결제 RESERVED 티켓 삭제 실패 - scheduleId=" + scheduleId
                + ", TICKETING 전이를 중단합니다.", e);
    }

    long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED);
    long remaining = schedule.getSeats() - confirmedCount;

    schedule.startTicketing();
    scheduleRepository.save(schedule);

    eventPublisher.publishEvent(new TicketingStartedEvent(
            scheduleId, remaining, schedule.getSeats(), schedule.getCookie(), schedule.getStartTime()));
}
```

cleanup 실패 시 `RuntimeException` → `@Transactional` 롤백 → `startTicketing()` 미반영 → Quartz misfire로 재시도 가능.