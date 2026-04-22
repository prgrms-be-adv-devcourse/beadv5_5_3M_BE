# Kafka·메시징 안정성 (KFK-001 ~ KFK-005)

> Kafka 컨슈머 에러 핸들링, 토픽 관리, 이벤트 데이터 완전성 관련 이슈와 해결 방법

---

## 핵심 원칙

Kafka 컨슈머에서 처리 불가능한 예외가 전파되면 Spring Kafka 기본 정책에 의해 동일 메시지를 무한 재시도 → 해당 파티션 전체 소비 중단 (Poison Pill). 모든 컨슈머의 `consume()` 메서드는 반드시 try-catch로 감싸고, 복구 불가 예외는 `log.error + return`으로 정상 소비 처리해야 한다.

---

## KFK-001: ScheduleEventConsumer 중복 메시지 예외 [CRITICAL → RESOLVED]

**파일:** `infrastructure/messaging/consumer/ScheduleEventConsumer.java`

### 현상

```java
if (scheduleRepository.existsById(request.scheduleId())) {
    throw ScheduleErrorCode.ALREADY_CONFIRMED.of(request.scheduleId());
    // → Kafka 재시도 → 동일 예외 무한 반복 → 파티션 블록
}
```

### 해결

```java
if (scheduleRepository.existsById(request.scheduleId())) {
    log.warn("중복 스케줄 수신 무시 - scheduleId={}", request.scheduleId());
    return; // 정상 소비 완료
}
```

---

## KFK-002: ScheduleEventConsumer 전반적 에러 핸들링 부재 [HIGH → RESOLVED]

**파일:** `infrastructure/messaging/consumer/ScheduleEventConsumer.java`

### 현상

KFK-001은 중복 케이스만 커버. JSON 파싱 실패, DB 커넥션 에러 등 나머지 런타임 예외는 모두 Kafka 재시도 루프에 빠짐.

### 해결

`consume()` 전체를 try-catch로 감싸고, KFK-001과 함께 적용:

```java
public void consume(String message) {
    try {
        ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);
        if (scheduleRepository.existsById(request.scheduleId())) {
            log.warn("중복 스케줄 수신 무시 - scheduleId={}", request.scheduleId());
            return;
        }
        // ... schedule 생성 로직
    } catch (Exception e) {
        log.error("movie.schedule.confirmed 처리 실패 - message={}", message, e);
    }
}
```

---

## KFK-003: QueueDrainConsumer 에러 핸들링 없음 [HIGH → RESOLVED]

**파일:** `infrastructure/messaging/consumer/QueueDrainConsumer.java`

### 현상

```java
public void consume(String message) {
    QueueDrainMessage msg = kafkaMessageUtil.deserialize(message, QueueDrainMessage.class);
    queueAutoProcessService.checkAndProcess(msg.scheduleId());
    // 예외 핸들링 없음
}
```

### 해결

```java
public void consume(String message) {
    try {
        QueueDrainMessage msg = kafkaMessageUtil.deserialize(message, QueueDrainMessage.class);
        queueAutoProcessService.checkAndProcess(msg.scheduleId());
    } catch (Exception e) {
        log.error("queue.drain 처리 실패 - message={}", message, e);
    }
}
```

---

## KFK-004: Kafka Topic 이름 중앙 관리 없음 [MEDIUM → RESOLVED]

**파일:** `infrastructure/messaging/KafkaTopics.java` (신규)

### 현상

토픽 이름이 5개 파일에 `private static final String`으로 분산 선언. `"queue.drain"`은 `TicketEventListener`과 `SelfPaymentService`에 중복. 토픽 이름 변경 시 누락 위험.

### 해결

`KafkaTopics` final 상수 클래스 생성:

```java
public final class KafkaTopics {
    // outbound — ticket lifecycle
    public static final String TICKET_RESERVED  = "ticket.reserved";
    public static final String TICKET_CANCELLED = "ticket.cancelled";
    public static final String TICKET_PAID      = "ticket.paid";
    public static final String TICKET_REFUNDED  = "ticket.refunded";
    public static final String TICKET_PROVIDE   = "ticket.provide";

    // outbound — schedule lifecycle
    public static final String CART_CLOSED       = "cart.closed";
    public static final String TICKETING_STARTED = "ticketing.started";

    // outbound — review
    public static final String REVIEW_AUTHORIZED = "ticket.review.authorized";

    // internal — queue management
    public static final String QUEUE_DRAIN      = "queue.drain";
    public static final String QUEUE_TERMINATED = "queue.terminated";
}
```

5개 파일(`TicketEventListener`, `SelfPaymentService`, `QueueAutoProcessService`, `ReviewAuthService`, `TicketProvideBatchConfig`)의 분산 상수를 `KafkaTopics.XXX` 참조로 교체.

---

## KFK-005: CartClosedEvent userIds 미포함 [MEDIUM → RESOLVED]

**파일:** `application/event/CartClosedEvent.java`, `infrastructure/messaging/dto/event/CartClosedMessage.java`, `application/service/CartCloseService.java`

### 현상

`CartCloseService` Case B(선착순 대기열 모드)에서 cart만 삭제되고, `cart.closed` Kafka 이벤트에 사용자 목록이 포함되지 않아 다운스트림에서 알림 대상을 식별할 수 없음.

### 해결

`CartClosedEvent`와 `CartClosedMessage`에 `List<UUID> userIds` 필드 추가:

```java
public record CartClosedEvent(Long scheduleId, String caseType, Integer seats, List<UUID> userIds) {}
public record CartClosedMessage(Long scheduleId, String caseType, Integer seats, List<UUID> userIds) {}
```

`CartCloseService`에서 장바구니 사용자 ID 수집하여 이벤트에 전달:

```java
List<UUID> userIds = carts.stream().map(Cart::getUserId).toList();
eventPublisher.publishEvent(new CartClosedEvent(scheduleId, caseType, schedule.getSeats(), userIds));
```