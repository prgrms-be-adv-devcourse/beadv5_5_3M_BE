# Kafka Message Schemas

`streaming-service` 가 소비하는 Kafka 토픽 2개의 JSON payload, 수신 DTO 설계, idempotency 전략.

---

## 1. 토픽 목록

| 토픽 | 방향 | 프로듀서 | key | 용도 |
|---|---|---|---|---|
| `movie.schedule.confirmed` | inbound | creator-service `MovieEventPublisher` | `scheduleId.toString()` | 스케줄 사본 적재 + Quartz 6 Job 등록 |
| `ticket.review.authorized` | inbound | ticket-service `ReviewAuthQuartzJob` | `ticketId.toString()` | Entitlement 적재 |

기존 미사용 (streaming-service 범위 외): `movie.uploaded`, `movie.updated`, `movie.deleted`, `movie.visibility`, `creator.created` 등은 소비 대상 아님.

---

## 2. `movie.schedule.confirmed`

### 2.1 프로듀서 레코드 (creator-service)

`creator-service/src/main/java/com/example/creatorservice/infrastructure/kafka/dto/ScheduleConfirmedMessage.java` 원본 그대로:

```java
public record ScheduleConfirmedMessage(
    Long scheduleId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime ticketingTime,
    String title,
    Integer cookie,
    UUID creatorId,
    Long movieId,
    String imageUrl,
    Integer seats
) {}
```

직렬화: `tools.jackson.databind.ObjectMapper` (Jackson 3). LocalDateTime 은 ISO-8601 문자열.

### 2.2 JSON 예시

```json
{
  "scheduleId": 42,
  "startTime": "2026-04-22T19:00:00",
  "endTime": "2026-04-22T21:00:00",
  "ticketingTime": "2026-04-21T19:00:00",
  "title": "봄밤의 라이브 상영",
  "cookie": 1000,
  "creatorId": "00000000-0000-0000-0000-000000000001",
  "movieId": 100,
  "imageUrl": "posters/abc/poster.jpg",
  "seats": 500
}
```

### 2.3 streaming-service 수신 DTO

**패키지**: `com.example.streamingservice.infrastructure.messaging.dto`

```java
public record ScheduleConfirmedPayload(
    Long scheduleId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime ticketingTime,  // 이 서비스는 무시
    String title,
    Integer cookie,               // 이 서비스는 무시
    UUID creatorId,
    Long movieId,
    String imageUrl,
    Integer seats
) {}
```

프로듀서 record 와 필드 1:1. 직렬화 호환성 유지를 위해 필드 개수 동일 유지 (ticketingTime/cookie 는 무시하지만 선언은 필요).

### 2.4 이 서비스가 실제로 쓰는 필드

- `scheduleId` → `Schedule.scheduleId` (PK)
- `startTime` → `Schedule.startTime` (UTC `Instant` 로 변환. 업스트림 LocalDateTime 은 KST 가정 → `ZoneId.of("Asia/Seoul")` 명시, 또는 프로듀서/컨슈머 타임존 합의 필요)
- `endTime` → `Schedule.endTime` (동일 변환)
- `title` → `Schedule.title`
- `creatorId` → `Schedule.creatorId`
- `movieId` → `Schedule.movieId`
- `imageUrl` → `Schedule.imageUrl`
- `seats` → `Schedule.seats`

**무시**: `ticketingTime`, `cookie` — ticket 서비스 관심사.

### 2.5 멱등성

- 키: `scheduleId` (PK).
- 재수신 처리: `Schedule` 이 이미 존재하면 스킵 (기존 엔티티 반환), 없으면 생성. Quartz Job 은 `replaceExisting=true` 로 항상 재등록 (JobKey 가 고정되어 안전).
- 구현 위치: `infrastructure/messaging/ScheduleConfirmedListener.onMessage` → `SchedulerPort.scheduleLifecycle(schedule)`.

### 2.6 타임존 처리 원칙

- 업스트림은 `LocalDateTime` 를 보냄 — 타임존 정보 없음. 암묵적 KST 가정.
- 수신 시 `Instant` 로 변환: `payload.startTime().atZone(ZoneId.of("Asia/Seoul")).toInstant()` (`ScheduleConfirmedListener` 내부 상수 `KST`).
- 저장·비교·Quartz trigger 는 모두 `Instant` 로만 처리 (UTC 기준).
- 도메인 층(Schedule 엔티티)은 `Instant` 만 본다.

---

## 3. `ticket.review.authorized`

### 3.1 프로듀서 레코드 (ticket-service)

`ticket-service/src/main/java/com/example/ticketservice/infrastructure/messaging/dto/event/ReviewAuthorizationMessage.java` 원본:

```java
public record ReviewAuthorizationMessage(
    Long ticketId,
    Long movieId,
    Long scheduleId,
    UUID userId
) {}
```

### 3.2 JSON 예시

```json
{
  "ticketId": 1001,
  "movieId": 100,
  "scheduleId": 42,
  "userId": "11111111-1111-1111-1111-111111111111"
}
```

### 3.3 streaming-service 수신 DTO

```java
public record TicketReviewAuthorizedPayload(
    Long ticketId,
    Long movieId,      // 이 서비스는 무시
    Long scheduleId,
    UUID userId
) {}
```

### 3.4 이 서비스가 쓰는 필드

- `ticketId` → `Entitlement.ticketId`
- `scheduleId` → `Entitlement.scheduleId`
- `userId` → `Entitlement.userId`
- `authorizedAt` → 수신 시점 `Instant.now()` 로 세팅

**무시**: `movieId` — `schedule` 에서 lookup 가능.

### 3.5 멱등성

- 유니크 제약: `(userId, scheduleId)`.
- 재수신 처리: 저장 전에 `EntitlementRepository.find(userId, scheduleId)` 로 선조회 후 존재하면 return. race condition 방어로 `DataIntegrityViolationException` 캐치 → DEBUG 로그 후 조용히 무시.
- 구현 위치: `infrastructure/messaging/TicketReviewAuthorizedListener.onMessage`.

---

## 4. Consumer 설정

### 4.1 Group & Deserialization

```yaml
spring:
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: streaming-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

**Deserialization 방식**: Spring Kafka `JsonDeserializer` 를 쓰지 않는다. 값은 `String` 으로 받고 리스너 내부에서 `KafkaMessageUtil.deserialize(message, TargetClass.class)` 로 명시적 파싱. 이유: trusted-packages 설정 복잡성 회피 + 타입 변환을 리스너가 완전히 제어.

### 4.2 @RetryableTopic

실제 listener 패턴 (`ScheduleConfirmedListener`, `TicketReviewAuthorizedListener` 공통):

```java
@RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000, multiplier = 2.0))
@KafkaListener(topics = "movie.schedule.confirmed", groupId = "${spring.kafka.consumer.group-id}")
@Transactional
public void onMessage(@Payload String message) {
    ScheduleConfirmedPayload payload = kafkaMessageUtil.deserialize(message, ScheduleConfirmedPayload.class);
    // ... 처리
}

@DltHandler
public void dlt(String message, Exception e) {
    log.error("Schedule confirmed DLT: payload={}", message, e);
}
```

- 재시도 간격: 1s → 2s → 4s (총 3회). 실패 시 DLT.
- DLT 토픽: `movie.schedule.confirmed-dlt`, `ticket.review.authorized-dlt` (Spring 자동 생성 접미사 `-dlt`).
- `@DltHandler` 는 현재 로그만. 운영에서는 DLT 모니터링 대시보드 연결 필요.

### 4.3 수동 ack 여부

- 기본 at-least-once. 수동 ack 사용 안 함.
- 멱등성은 DB unique 제약 + upsert 로 보장 (위 §2.5, §3.5).

---

## 5. 순서 보장 및 레이스 조건

### 5.1 Entitlement 이 Schedule 보다 먼저 도착

시나리오: ticket-service `ReviewAuthQuartzJob` 은 `startTime - 10m` 에 발화하고 creator-service 가 그보다 앞서 `movie.schedule.confirmed` 를 쏘는 것이 정상 순서. 그러나 메시지 지연으로 역순 도착 가능.

처리:
- `Entitlement` 저장 시 `Schedule` FK 위반 → `DataIntegrityViolationException` → `@RetryableTopic` 재시도 (3회, backoff 1s/2s/4s).
- 3회 내 Schedule 수신 확률이 매우 높음 — ticket-service 는 LOBBY 개방 10분 전에 쏘므로 실제로 역순 발생 드묾.
- 3회 재시도 실패 시 DLT → 수동 개입.

### 5.2 같은 `scheduleId` 의 `confirmed` 두 번 도착

정상 상황: ticket 메시지 재발행 (리플레이) 또는 creator-service 수정.

처리: `ScheduleConfirmedListener` 가 존재하는 `scheduleId` 이면 스킵, Quartz `scheduleLifecycle` 은 JobKey 기반 `replaceExisting=true` 로 안전하게 재등록. 5.1 과 마찬가지로 멱등.

### 5.3 같은 `(userId, scheduleId)` 의 `authorized` 두 번

처리: DB unique 위반 → 조용히 무시. 로그 INFO 로 가시성만.

### 5.4 Consumer 처리 중 장애

Spring Kafka 기본 동작: ack 안 된 메시지 → 재전달. `@RetryableTopic` 이 재시도 토픽을 자동 생성해 backoff.

---

## 6. DTO 변환 유틸리티

**LocalDateTime → Instant 변환** (`ScheduleConfirmedListener` 내부 상수):

```java
private static final ZoneId KST = ZoneId.of("Asia/Seoul");
// payload.startTime().atZone(KST).toInstant()
```

**JSON → record 역직렬화**: `infrastructure/util/KafkaMessageUtil.deserialize(message, Class<T>)` — 내부 `ObjectMapper` 사용.

---

## 7. 관련 문서

- `DOMAIN-MODEL.md §2/§3` — 엔티티 필드 매핑 대상
- `DESIGN.md §5.1~5.2` — ingest 플로우 다이어그램
- `CONFIG.md §3` — Kafka 설정 전체
