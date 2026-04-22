# 메모리·쿼리 성능 (PERF-001 ~ PERF-004)

> N+1 쿼리, 전체 메모리 로딩, 무한 루프 위험 관련 이슈와 해결 방법

---

## 핵심 원칙

대량 데이터를 다루는 로직은 반드시 페이지네이션 또는 배치 단위 처리를 적용한다. `findAll()` 류 전체 로딩은 데이터 증가 시 OOM 직결. 반복문은 항상 상한을 두어 외부 시스템(Redis 등) 장애 시 무한 루프를 방지한다.

---

## PERF-001: CartService.getMyCart() N+1 쿼리 [HIGH → RESOLVED]

**파일:** `application/service/CartService.java`

### 현상

```java
List<Cart> carts = cartRepository.findAllByUserId(userId);
return carts.stream()
    .map(cart -> {
        Schedule schedule = scheduleRepository.findById(cart.getScheduleId())  // 카트당 1회 DB 조회
                .orElseThrow(...);
        return CartItemResponse.from(cart, schedule);
    })
    .toList();
// 카트 N개 → SELECT 1 + N회
```

### 해결 — findAllById 일괄 조회

```java
List<Long> scheduleIds = carts.stream().map(Cart::getScheduleId).toList();
Map<Long, Schedule> scheduleMap = scheduleRepository.findAllById(scheduleIds).stream()
        .collect(Collectors.toMap(Schedule::getId, Function.identity()));

return carts.stream()
        .map(cart -> {
            Schedule schedule = scheduleMap.get(cart.getScheduleId());
            if (schedule == null) {
                throw ScheduleErrorCode.NOT_FOUND.of(cart.getScheduleId());
            }
            return CartItemResponse.from(cart, schedule);
        })
        .toList();
// SELECT 2회 (carts + schedules)
```

---

## PERF-002: TicketProvideBatchConfig 전체 티켓 메모리 로딩 [CRITICAL → RESOLVED]

**파일:** `infrastructure/batch/TicketProvideBatchConfig.java`, `infrastructure/persistence/TicketJpaRepository.java`

### 현상

```java
List<Ticket> tickets = ticketRepository.findAllByStatusAndProvideFlag(CONFIRMED, false);
// 50K+ 티켓 전체 로드 → OOM 위험
```

### 해결 — Slice 기반 페이지네이션 Tasklet

`TicketJpaRepository`에 페이지네이션 메서드 추가:

```java
Slice<Ticket> findByStatusAndProvideFlag(TicketStatus status, boolean provideFlag, Pageable pageable);
```

Tasklet에서 do-while 루프로 500건씩 처리:

```java
private static final int PAGE_SIZE = 500;

Slice<Ticket> slice;
do {
    slice = ticketJpaRepository.findByStatusAndProvideFlag(
            TicketStatus.CONFIRMED, false, PageRequest.of(0, PAGE_SIZE));

    List<Ticket> tickets = slice.getContent();
    if (tickets.isEmpty()) break;

    // Kafka 메시지 수집
    List<DailyTicketFeeProvideMessage> messages = tickets.stream()
            .map(t -> new DailyTicketFeeProvideMessage(
                    t.getSchedule().getCreatorId(), t.getId(),
                    t.getSchedule().getId(), t.getCookie()))
            .toList();

    // DB 벌크 업데이트
    List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
    ticketJpaRepository.bulkMarkProvided(ticketIds);

    // Kafka 발행
    messages.forEach(msg ->
            eventPublisherPort.publish(KafkaTopics.TICKET_PROVIDE, msg.ticketId().toString(), msg));
} while (slice.hasNext());
```

**핵심:** `bulkMarkProvided()`가 `provideFlag=true`로 변경하므로 항상 `page=0`으로 조회해도 다음 반복에서 미처리 건만 조회된다.

---

## PERF-003: ReviewAuthService 전체 티켓 메모리 로딩 [MEDIUM → RESOLVED]

**파일:** `application/service/ReviewAuthService.java`

### 현상

```java
List<Ticket> tickets = ticketRepository.findAllByScheduleIdAndStatus(scheduleId, CONFIRMED);
// 전체 티켓 메모리 로드 → 대규모 스케줄에서 메모리 부담
```

### 해결 — Slice 페이지네이션

```java
private static final int PAGE_SIZE = 500;

int page = 0;
Slice<Ticket> slice;
do {
    slice = ticketRepository.findByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED, page, PAGE_SIZE);

    for (Ticket ticket : slice.getContent()) {
        eventPublisherPort.publish(
                KafkaTopics.REVIEW_AUTHORIZED,
                ticket.getId().toString(),
                new ReviewAuthorizationMessage(
                        ticket.getId(), ticket.getSchedule().getMovieId(),
                        scheduleId, ticket.getUserId()));
    }

    page++;
} while (slice.hasNext());
```

---

## PERF-004: drainQueue() while(true) Redis 장애 시 무한 루프 위험 [HIGH → RESOLVED]

**파일:** `application/service/QueueAutoProcessService.java`

### 현상

```java
private void drainQueue(Long scheduleId) {
    while (true) {
        Long stock = cachePort.getCounter(STOCK_KEY_PREFIX + scheduleId);
        Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);
        if (stock <= 0 || queueSize == 0) break;
        // ... 처리 로직
    }
    // Redis 장애 시 stock/queueSize가 stale 값 반환 → break 조건 미충족 → 무한 루프
}
```

### 해결 — 상한 제한 for 루프

```java
private static final int MAX_DRAIN_ITERATIONS = 500;

private void drainQueue(Long scheduleId) {
    for (int i = 0; i < MAX_DRAIN_ITERATIONS; i++) {
        Long stock = cachePort.getCounter(STOCK_KEY_PREFIX + scheduleId);
        Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);

        if (stock == null || stock <= 0 || queueSize == null || queueSize == 0) {
            break;
        }

        int window = (int) Math.min(stock, queueSize);
        List<PurchaseTask> tasks = collectWindow(scheduleId, window);

        if (tasks.isEmpty()) break;

        processWindowParallel(scheduleId, tasks);
    }
}
```

`MAX_DRAIN_ITERATIONS=500`은 좌석 수 상한. Redis 장애로 break 조건 미충족 시에도 500회 후 안전 종료.