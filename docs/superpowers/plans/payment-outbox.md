# payment-service Transactional Outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** payment-service의 `payment.confirmed` / `payment.failed` / `payment.refunded` 이벤트를 Transactional Outbox + 앱 폴링 릴레이로 발행하여 DB 커밋과 Kafka 발행의 원자성(at-least-once)을 보장한다.

**Architecture:** `PaymentService` / `RefundService`가 비즈니스 상태 변경과 같은 트랜잭션에서 `outbox_messages` 테이블에 `PENDING` row를 삽입한다. `OutboxRelay`가 500ms 간격으로 `FOR UPDATE SKIP LOCKED`를 사용해 배치로 읽고 Kafka로 발행 후 `PUBLISHED`로 마킹한다. 실패 시 exponential backoff로 재시도, MAX 초과 시 `FAILED`.

**Tech Stack:** Spring Boot 4.0.4, Java 21, JPA/Hibernate, PostgreSQL, Spring Kafka, Lombok, JUnit5, H2 (test), Mockito

**브랜치:** `fix/payment/outbox` (이미 생성됨, 베이스: `origin/dev/payment`)

**커밋 정책:** 각 Task 끝의 **Commit 스텝은 사용자가 수동 실행.** 이 계획서의 커밋 메시지는 제안 문구이며, 에이전트는 `git add` / `git commit`을 실행하지 않는다.

**파일 구조 (신규/수정/삭제):**

```
payment-service/src/main/java/com/example/paymentservice/
├── PaymentServiceApplication.java                                      [MOD]  +@EnableScheduling
├── common/
│   ├── messaging/
│   │   ├── dto/PaymentConfirmedMessage.java                            (유지)
│   │   ├── dto/PaymentRefundedMessage.java                             (유지)
│   │   ├── dto/PaymentFailedMessage.java                               [NEW]  실패 이벤트 payload
│   │   ├── PaymentTopics.java                                          (유지)
│   │   └── KafkaEventPublisher.java                                    [DEL]  AFTER_COMMIT 리스너 제거
│   └── outbox/
│       ├── OutboxStatus.java                                           [NEW]  enum
│       ├── OutboxProperties.java                                       [NEW]  @ConfigurationProperties
│       ├── application/
│       │   ├── OutboxEnqueuer.java                                     [NEW]  service → outbox 래퍼
│       │   └── OutboxRelay.java                                        [NEW]  @Scheduled 릴레이
│       ├── domain/
│       │   ├── model/OutboxMessage.java                                [NEW]  JPA 엔티티
│       │   └── repository/OutboxRepository.java                        [NEW]  port
│       └── infrastructure/
│           ├── OutboxJpaRepository.java                                [NEW]  Spring Data + native query
│           └── OutboxRepositoryAdapter.java                            [NEW]  port 구현
├── payment/application/
│   ├── PaymentService.java                                             [MOD]  eventPublisher → outboxEnqueuer
│   ├── event/PaymentCompletedEvent.java                                [DEL]
│   └── event/PaymentFailedEvent.java                                   [DEL]
└── refund/application/
    ├── RefundService.java                                              [MOD]  eventPublisher → outboxEnqueuer
    └── event/RefundApprovedEvent.java                                  [DEL]

payment-service/src/main/resources/
└── application-prod.yaml                                               [MOD]  producer 강화 + outbox 섹션

payment-service/src/test/java/com/example/paymentservice/common/outbox/
├── domain/model/OutboxMessageTest.java                                 [NEW]
├── application/OutboxEnqueuerTest.java                                 [NEW]
└── application/OutboxRelayTest.java                                    [NEW]
```

---

## Phase 1: Outbox 도메인 (엔티티 + Port)

### Task 1: OutboxStatus enum

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/OutboxStatus.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxStatus enum 추가
```

---

### Task 2: OutboxMessage 엔티티 + 팩토리 / 상태 전이 메서드

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/domain/model/OutboxMessage.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.domain.model;

import com.example.paymentservice.common.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_next_retry",
                        columnList = "status, next_retry_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;      // "PAYMENT" or "REFUND"

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;        // paymentId/refundId 문자열 (Kafka key)

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static OutboxMessage pending(String aggregateType,
                                        String aggregateId,
                                        String topic,
                                        String payload) {
        OutboxMessage m = new OutboxMessage();
        m.messageId = UUID.randomUUID();
        m.aggregateType = aggregateType;
        m.aggregateId = aggregateId;
        m.topic = topic;
        m.payload = payload;
        m.status = OutboxStatus.PENDING;
        m.retryCount = 0;
        m.createdAt = LocalDateTime.now();
        m.nextRetryAt = m.createdAt;
        return m;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markRetry(String error, LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.lastError = truncate(error);
        this.nextRetryAt = nextRetryAt;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxMessage 엔티티 추가
```

---

### Task 3: OutboxMessage 단위 테스트

**Files:**
- Create: `payment-service/src/test/java/com/example/paymentservice/common/outbox/domain/model/OutboxMessageTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.example.paymentservice.common.outbox.domain.model;

import com.example.paymentservice.common.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageTest {

    @Test
    void pending_상태로_생성된다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "42", "payment.confirmed", "{}");

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getRetryCount()).isZero();
        assertThat(m.getMessageId()).isNotNull();
        assertThat(m.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(m.getAggregateId()).isEqualTo("42");
        assertThat(m.getTopic()).isEqualTo("payment.confirmed");
        assertThat(m.getPayload()).isEqualTo("{}");
        assertThat(m.getNextRetryAt()).isNotNull();
        assertThat(m.getCreatedAt()).isNotNull();
    }

    @Test
    void markPublished_상태와_publishedAt_을_세팅한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");

        m.markPublished();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(m.getPublishedAt()).isNotNull();
    }

    @Test
    void markRetry_retryCount_증가하고_nextRetryAt_을_갱신한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");
        LocalDateTime next = LocalDateTime.now().plusSeconds(10);

        m.markRetry("boom", next);

        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).isEqualTo("boom");
        assertThat(m.getNextRetryAt()).isEqualTo(next);
        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void markFailed_상태와_에러를_저장한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");

        m.markFailed("max exceeded");

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).isEqualTo("max exceeded");
    }

    @Test
    void lastError_2000자_초과시_잘린다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");
        String longError = "x".repeat(2500);

        m.markFailed(longError);

        assertThat(m.getLastError()).hasSize(2000);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 통과 확인**

Run: `./gradlew :payment-service:test --tests OutboxMessageTest`
Expected: 5 tests, all pass

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
test(payment): OutboxMessage 상태 전이 단위 테스트
```

---

### Task 4: OutboxRepository port (도메인 인터페이스)

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/domain/repository/OutboxRepository.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.domain.repository;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository {

    OutboxMessage save(OutboxMessage message);

    /**
     * 릴레이가 처리할 PENDING row를 잠금과 함께 가져온다.
     * Postgres의 FOR UPDATE SKIP LOCKED를 사용해 여러 replica 간 경합 방지.
     */
    List<OutboxMessage> findPendingForRelay(LocalDateTime now, int batchSize);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxRepository port 추가
```

---

### Task 5: OutboxJpaRepository (native SKIP LOCKED)

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/infrastructure/OutboxJpaRepository.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.infrastructure;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Postgres: status=PENDING, next_retry_at <= now 인 row를 id 오름차순으로
     * 배치 크기만큼 잠금 획득 (다른 replica가 이미 잡은 row는 skip).
     *
     * H2 테스트 환경에서는 FOR UPDATE SKIP LOCKED가 무시되거나 미지원일 수 있으므로
     * OutboxRelayTest는 Mockito로 Repository를 모킹한다.
     */
    @Query(
            value = """
                    SELECT * FROM outbox_messages
                    WHERE status = 'PENDING' AND next_retry_at <= :now
                    ORDER BY id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxMessage> findPendingForRelay(@Param("now") LocalDateTime now,
                                            @Param("batchSize") int batchSize);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxJpaRepository + SKIP LOCKED 쿼리
```

---

### Task 6: OutboxRepositoryAdapter

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/infrastructure/OutboxRepositoryAdapter.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.infrastructure;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    @Override
    public OutboxMessage save(OutboxMessage message) {
        return jpaRepository.save(message);
    }

    @Override
    public List<OutboxMessage> findPendingForRelay(LocalDateTime now, int batchSize) {
        return jpaRepository.findPendingForRelay(now, batchSize);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxRepositoryAdapter (port 구현)
```

---

## Phase 2: 설정 및 페이로드 DTO

### Task 7: OutboxProperties

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/OutboxProperties.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.outbox")
public record OutboxProperties(
        int batchSize,
        long pollDelayMs,
        long sendTimeoutMs,
        int maxRetries,
        int backoffBaseSeconds
) {
    public OutboxProperties {
        if (batchSize <= 0) batchSize = 100;
        if (pollDelayMs <= 0) pollDelayMs = 500L;
        if (sendTimeoutMs <= 0) sendTimeoutMs = 3_000L;
        if (maxRetries <= 0) maxRetries = 5;
        if (backoffBaseSeconds <= 0) backoffBaseSeconds = 10;
    }
}
```

- [ ] **Step 2: `PaymentServiceApplication.java`에 `@ConfigurationPropertiesScan` 추가 (아직 없다면)**

Verify that `PaymentServiceApplication` already has `@ConfigurationPropertiesScan` or the class-level `@EnableConfigurationProperties(OutboxProperties.class)` is needed. Read [PaymentServiceApplication.java](payment-service/src/main/java/com/example/paymentservice/PaymentServiceApplication.java) first, then:

- 만약 `@ConfigurationPropertiesScan`이 없다면 추가 (다음 Task 16에서 `@EnableScheduling`과 함께 처리)
- 만약 `TossPaymentProperties` 같은 기존 `@ConfigurationProperties`가 이미 쓰이는 방식을 확인해서 동일 패턴 적용

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxProperties 설정 바인딩 추가
```

---

### Task 8: application-prod.yaml에 outbox 섹션 추가 + producer 강화

**Files:**
- Modify: `payment-service/src/main/resources/application-prod.yaml`

- [ ] **Step 1: Kafka producer 섹션 수정 (기존 값 강화)**

기존 45~54라인 근처 `producer:` 블록을 아래로 교체:

```yaml
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 10
      properties:
        spring.json.add.type.headers: false
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        max.block.ms: 3000
        retry.backoff.ms: 1000
        delivery.timeout.ms: 30000
        request.timeout.ms: 10000
```

- [ ] **Step 2: 파일 끝에 outbox 섹션 추가**

```yaml
payment:
  outbox:
    batch-size: 100
    poll-delay-ms: 500
    send-timeout-ms: 3000
    max-retries: 5
    backoff-base-seconds: 10
```

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
chore(payment): Kafka producer idempotence + outbox 설정 값 추가
```

---

### Task 9: PaymentFailedMessage DTO

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/messaging/dto/PaymentFailedMessage.java`

기존 `PaymentFailedEvent` Spring 이벤트는 삭제될 예정이므로, outbox payload용 DTO를 만든다. (`PaymentConfirmedMessage`, `PaymentRefundedMessage` 패턴과 일치)

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

//topic: payment.failed
//receiver: user-service (informational)
public record PaymentFailedMessage(
        Long paymentId,
        UUID userId,
        LocalDateTime createdAt
) {
    public static PaymentFailedMessage of(Long paymentId, UUID userId) {
        return new PaymentFailedMessage(paymentId, userId, LocalDateTime.now());
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): PaymentFailedMessage DTO 추가 (outbox payload)
```

---

## Phase 3: OutboxEnqueuer (service 편의 래퍼)

### Task 10: OutboxEnqueuer 테스트 먼저 작성

**Files:**
- Create: `payment-service/src/test/java/com/example/paymentservice/common/outbox/application/OutboxEnqueuerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.messaging.PaymentTopics;
import com.example.paymentservice.common.messaging.dto.PaymentConfirmedMessage;
import com.example.paymentservice.common.outbox.OutboxStatus;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEnqueuerTest {

    private OutboxRepository repository;
    private OutboxEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        when(repository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        enqueuer = new OutboxEnqueuer(repository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void enqueue_는_PENDING_상태_OutboxMessage_를_저장한다() {
        UUID userId = UUID.randomUUID();
        PaymentConfirmedMessage payload = PaymentConfirmedMessage.of(42L, userId, 10000, 100);

        enqueuer.enqueue("PAYMENT", "42", PaymentTopics.PAYMENT_CONFIRMED, payload);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        OutboxMessage saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getTopic()).isEqualTo(PaymentTopics.PAYMENT_CONFIRMED);
        assertThat(saved.getPayload()).contains("\"paymentId\":42");
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인 (클래스 없음)**

Run: `./gradlew :payment-service:test --tests OutboxEnqueuerTest`
Expected: compile error — `OutboxEnqueuer` not found

---

### Task 11: OutboxEnqueuer 구현

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/application/OutboxEnqueuer.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEnqueuer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 서비스 트랜잭션 안에서 호출. 같은 TX로 커밋된다.
     * payload는 JSON으로 직렬화되어 outbox_messages.payload에 저장된다.
     */
    public void enqueue(String aggregateType, String aggregateId, String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] payload 직렬화 실패 - topic={}, aggregateId={}", topic, aggregateId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        outboxRepository.save(OutboxMessage.pending(aggregateType, aggregateId, topic, json));
    }
}
```

- [ ] **Step 2: 테스트 통과 확인**

Run: `./gradlew :payment-service:test --tests OutboxEnqueuerTest`
Expected: 1 test, pass

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxEnqueuer — 서비스 TX 안 outbox 저장 래퍼
```

---

## Phase 4: OutboxRelay (@Scheduled)

### Task 12: OutboxRelay 테스트 먼저 작성

**Files:**
- Create: `payment-service/src/test/java/com/example/paymentservice/common/outbox/application/OutboxRelayTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.outbox.OutboxProperties;
import com.example.paymentservice.common.outbox.OutboxStatus;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private OutboxRepository repository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        OutboxProperties props = new OutboxProperties(100, 500L, 3_000L, 5, 10);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        relay = new OutboxRelay(repository, kafkaTemplate, objectMapper, props);
    }

    private OutboxMessage pending(long id) {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", String.valueOf(id),
                "payment.confirmed", "{\"paymentId\":" + id + "}");
        // id 설정은 리플렉션 필요 없음 — 테스트에선 id 사용 X
        return m;
    }

    private CompletableFuture<SendResult<String, Object>> successFuture() {
        SendResult<String, Object> result = mock(SendResult.class);
        when(result.getRecordMetadata()).thenReturn(
                new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0));
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<SendResult<String, Object>> failedFuture(String reason) {
        CompletableFuture<SendResult<String, Object>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException(reason));
        return f;
    }

    @Test
    void 성공_발행_시_PUBLISHED_로_마킹한다() {
        OutboxMessage m = pending(1L);
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(m.getPublishedAt()).isNotNull();
        verify(repository).save(m);
    }

    @Test
    void 실패시_retryCount_증가하고_PENDING_유지한다() {
        OutboxMessage m = pending(1L);
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("boom"));

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).contains("boom");
        verify(repository).save(m);
    }

    @Test
    void maxRetries_도달_직전_실패하면_FAILED_로_전이한다() {
        OutboxMessage m = pending(1L);
        // 4번까지 실패한 상태를 시뮬레이트 (maxRetries=5)
        for (int i = 0; i < 4; i++) {
            m.markRetry("prev", m.getNextRetryAt());
        }
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("final"));

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(m.getRetryCount()).isEqualTo(5);
        verify(repository).save(m);
    }

    @Test
    void findPending_결과가_비면_Kafka_호출_안_한다() {
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of());

        relay.relay();

        verifyNoInteractions(kafkaTemplate);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인 (클래스 없음)**

Run: `./gradlew :payment-service:test --tests OutboxRelayTest`
Expected: compile error — `OutboxRelay` not found

---

### Task 13: OutboxRelay 구현

**Files:**
- Create: `payment-service/src/main/java/com/example/paymentservice/common/outbox/application/OutboxRelay.java`

- [ ] **Step 1: 파일 생성**

```java
package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.outbox.OutboxProperties;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    /**
     * 주기적으로 PENDING 메시지를 가져와 Kafka로 발행한다.
     *
     * @Transactional: findPendingForRelay의 FOR UPDATE SKIP LOCKED 잠금을 유지하기 위해
     *                 트랜잭션이 필요하다. 배치 처리 후 커밋되면 잠금이 해제된다.
     */
    @Scheduled(fixedDelayString = "${payment.outbox.poll-delay-ms:500}")
    @Transactional
    public void relay() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxMessage> batch = outboxRepository.findPendingForRelay(now, properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessage msg : batch) {
            publishOne(msg);
        }
    }

    private void publishOne(OutboxMessage msg) {
        try {
            // payload는 outbox_messages.payload 컬럼의 JSON 문자열.
            // Producer의 value-serializer가 JsonSerializer라, String을 그대로 넘기면
            // 이중 직렬화되어 이스케이프된 JSON이 브로커에 저장된다.
            // JsonNode로 파싱해서 넘기면 JsonSerializer가 정상 직렬화하며,
            // Consumer 쪽은 StringDeserializer로 JSON 문자열을 받아 파싱하는 기존 계약을 유지.
            JsonNode value = objectMapper.readTree(msg.getPayload());

            kafkaTemplate
                    .send(msg.getTopic(), msg.getAggregateId(), value)
                    .get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            msg.markPublished();
            outboxRepository.save(msg);
        } catch (Exception e) {
            handleFailure(msg, e);
        }
    }

    private void handleFailure(OutboxMessage msg, Exception e) {
        int nextRetry = msg.getRetryCount() + 1;
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (nextRetry >= properties.maxRetries()) {
            log.error("[Outbox] MAX 재시도 초과 - FAILED 전이. messageId={}, topic={}, error={}",
                    msg.getMessageId(), msg.getTopic(), error, e);
            msg.markFailed(error);
        } else {
            long backoffSeconds = (long) (properties.backoffBaseSeconds() * Math.pow(2, msg.getRetryCount()));
            LocalDateTime next = LocalDateTime.now().plusSeconds(backoffSeconds);
            log.warn("[Outbox] 발행 실패 - 재시도 예약. messageId={}, retry={}/{}, nextRetryAt={}, error={}",
                    msg.getMessageId(), nextRetry, properties.maxRetries(), next, error);
            msg.markRetry(error, next);
        }
        outboxRepository.save(msg);
    }
}
```

- [ ] **Step 2: 테스트 통과 확인**

Run: `./gradlew :payment-service:test --tests OutboxRelayTest`
Expected: 4 tests, all pass

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
feat(payment): OutboxRelay — 폴링 릴레이 + 재시도/backoff/FAILED
```

---

## Phase 5: Service 통합

### Task 14: PaymentService 수정

**Files:**
- Modify: `payment-service/src/main/java/com/example/paymentservice/payment/application/PaymentService.java`

기존 `eventPublisher.publishEvent(PaymentCompletedEvent...)` / `PaymentFailedEvent` 호출을 `OutboxEnqueuer`로 교체한다.

- [ ] **Step 1: 파일 전체 교체**

```java
package com.example.paymentservice.payment.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.common.messaging.PaymentTopics;
import com.example.paymentservice.common.messaging.dto.PaymentConfirmedMessage;
import com.example.paymentservice.common.messaging.dto.PaymentFailedMessage;
import com.example.paymentservice.common.outbox.application.OutboxEnqueuer;
import com.example.paymentservice.payment.application.dto.PaymentConfirmCommand;
import com.example.paymentservice.payment.application.dto.PaymentInfo;
import com.example.paymentservice.payment.client.PaymentGateway;
import com.example.paymentservice.payment.client.PaymentGateway.PaymentGatewayResponse;
import com.example.paymentservice.payment.domain.PaymentStatus;
import com.example.paymentservice.payment.domain.model.Payment;
import com.example.paymentservice.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String AGGREGATE_PAYMENT = "PAYMENT";
    private static final int MAX_RETRIES = 3;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxEnqueuer outboxEnqueuer;
    private final TransactionTemplate txTemplate;

    public PaymentInfo confirmPayment(PaymentConfirmCommand command) {

        // ━━━ TX1: 멱등성 체크 + Payment 생성(IN_PROGRESS) ━━━
        Payment result = txTemplate.execute(status -> {
            Optional<Payment> existing = paymentRepository.findByOrderId(command.orderId());
            if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.SUCCESS) {
                return existing.get();
            }
            Payment payment = Payment.create(command.userId(), command.amount(), command.cookieAmount());
            return paymentRepository.save(payment);
        });

        if (result.getStatus() == PaymentStatus.SUCCESS) {
            log.info("[Payment] 멱등성 - 이미 완료된 결제: orderId={}", command.orderId());
            return PaymentInfo.from(result);
        }

        Payment inProgress = result;
        Long paymentId = inProgress.getId();
        log.info("[Payment] 결제 처리 시작 - paymentId: {}, amount: {}", paymentId, inProgress.getAmount());

        // ━━━ TX 밖: PG API 호출 ━━━
        PaymentGatewayResponse pgResponse = null;
        Exception pgException = null;
        try {
            pgResponse = paymentGateway.confirmPayment(
                    command.paymentKey(), command.orderId(), inProgress.getAmount());
        } catch (Exception e) {
            log.error("[Payment] PG 결제 확인 실패: paymentId={}, reason={}", paymentId, e.getMessage(), e);
            pgException = e;
        }

        // ━━━ TX2: 결과 반영 + outbox enqueue ━━━
        if (pgException != null) {
            txTemplate.executeWithoutResult(s -> {
                Payment p = paymentRepository.findById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
                p.markFailed();
                outboxEnqueuer.enqueue(
                        AGGREGATE_PAYMENT,
                        p.getId().toString(),
                        PaymentTopics.PAYMENT_FAILED,
                        PaymentFailedMessage.of(p.getId(), p.getUserId())
                );
            });
            throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED);
        }

        String pgPaymentKey = pgResponse.paymentKey();
        return retryCommit(paymentId, pgPaymentKey, command.orderId());
    }

    private PaymentInfo retryCommit(Long paymentId, String pgPaymentKey, String orderId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                PaymentInfo result = txTemplate.execute(s -> {
                    Payment p = paymentRepository.findById(paymentId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
                    p.markSuccess(pgPaymentKey, orderId);
                    outboxEnqueuer.enqueue(
                            AGGREGATE_PAYMENT,
                            p.getId().toString(),
                            PaymentTopics.PAYMENT_CONFIRMED,
                            PaymentConfirmedMessage.of(p.getId(), p.getUserId(),
                                    p.getAmount(), p.getCookieAmount())
                    );
                    return PaymentInfo.from(p);
                });
                log.info("[Payment] 결제 완료 - paymentId: {}", paymentId);
                return result;
            } catch (Exception e) {
                log.warn("[Payment] DB 저장 실패 (시도 {}/{}) - paymentId: {}",
                        attempt, MAX_RETRIES, paymentId);
                if (attempt == MAX_RETRIES) {
                    log.error("[Payment] 결제 DB 반영 최종 실패! paymentId={}", paymentId);
                    throw e;
                }
                try { Thread.sleep(500L * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    @Transactional
    public PaymentInfo failPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.markFailed();
        outboxEnqueuer.enqueue(
                AGGREGATE_PAYMENT,
                payment.getId().toString(),
                PaymentTopics.PAYMENT_FAILED,
                PaymentFailedMessage.of(payment.getId(), payment.getUserId())
        );
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsByUser(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL` — `PaymentCompletedEvent`, `PaymentFailedEvent`, `ApplicationEventPublisher` import 모두 제거됨

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
refactor(payment): PaymentService — Spring event 대신 outbox enqueue
```

---

### Task 15: RefundService 수정

**Files:**
- Modify: `payment-service/src/main/java/com/example/paymentservice/refund/application/RefundService.java`

- [ ] **Step 1: 파일 전체 교체**

```java
package com.example.paymentservice.refund.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.common.messaging.PaymentTopics;
import com.example.paymentservice.common.messaging.dto.PaymentRefundedMessage;
import com.example.paymentservice.common.outbox.application.OutboxEnqueuer;
import com.example.paymentservice.payment.domain.PaymentStatus;
import com.example.paymentservice.payment.domain.model.Payment;
import com.example.paymentservice.payment.domain.repository.PaymentRepository;
import com.example.paymentservice.refund.application.dto.RefundCommand;
import com.example.paymentservice.refund.application.dto.RefundInfo;
import com.example.paymentservice.refund.client.RefundGateway;
import com.example.paymentservice.refund.domain.RefundStatus;
import com.example.paymentservice.refund.domain.model.Refund;
import com.example.paymentservice.refund.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final int REFUND_AVAILABLE_DAYS = 7;
    private static final int MAX_RETRIES = 3;
    private static final String AGGREGATE_REFUND = "REFUND";

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final OutboxEnqueuer outboxEnqueuer;
    private final TransactionTemplate txTemplate;

    @Transactional
    public RefundInfo requestRefund(RefundCommand command) {
        Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        if (payment.getCreatedAt().plusDays(REFUND_AVAILABLE_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        if (command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        int totalRefundedCookies = refundRepository.findByPaymentId(command.paymentId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .mapToInt(Refund::getCookieAmount)
                .sum();
        if (totalRefundedCookies + command.cookieAmount() > payment.getCookieAmount()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }

        int wonAmount = BigDecimal.valueOf(command.cookieAmount())
                .multiply(BigDecimal.valueOf(payment.getAmount()))
                .divide(BigDecimal.valueOf(payment.getCookieAmount()), 0, RoundingMode.HALF_UP)
                .intValue();

        Refund refund = Refund.create(command.paymentId(), command.userId(), wonAmount, command.cookieAmount());
        return RefundInfo.from(refundRepository.save(refund));
    }

    public RefundInfo approveRefund(Long refundId) {
        Refund processingRefund = txTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
            refund.validatePending();
            refund.markProcessing();
            return refund;
        });

        Payment payment = paymentRepository.findById(processingRefund.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        String paymentKey = payment.getPaymentKey();
        int refundAmount = processingRefund.getAmount();

        log.info("[Refund] 환불 처리 시작 - refundId: {}, amount: {}", refundId, refundAmount);

        Exception pgException = null;
        try {
            refundGateway.cancelPayment(paymentKey, refundAmount, "환불 확인");
        } catch (Exception e) {
            log.error("[Refund] PG 환불 실패 - refundId: {}, paymentKey: {}, message: {}",
                    refundId, paymentKey, e.getMessage());
            pgException = e;
        }

        if (pgException != null) {
            txTemplate.executeWithoutResult(s -> {
                Refund r = refundRepository.findById(refundId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
                r.markProcessingFailed();
            });
            throw new BusinessException(ErrorCode.PG_REFUND_FAILED);
        }

        return retryCommit(refundId);
    }

    private RefundInfo retryCommit(Long refundId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                RefundInfo result = txTemplate.execute(s -> {
                    Refund r = refundRepository.findById(refundId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
                    r.markSuccess();
                    outboxEnqueuer.enqueue(
                            AGGREGATE_REFUND,
                            r.getId().toString(),
                            PaymentTopics.PAYMENT_REFUNDED,
                            PaymentRefundedMessage.of(r.getId(), r.getPaymentId(),
                                    r.getUserId(), r.getAmount(), r.getCookieAmount())
                    );
                    return RefundInfo.from(r);
                });
                log.info("[Refund] 환불 완료 - refundId: {}", refundId);
                return result;
            } catch (Exception e) {
                log.warn("[Refund] DB 저장 실패 (시도 {}/{}) - refundId: {}",
                        attempt, MAX_RETRIES, refundId);
                if (attempt == MAX_RETRIES) {
                    log.error("[Refund] 환불 DB 반영 최종 실패! refundId={}", refundId);
                    throw e;
                }
                try { Thread.sleep(500L * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    @Transactional
    public RefundInfo rejectRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        refund.markFailed();
        return RefundInfo.from(refund);
    }

    @Transactional(readOnly = true)
    public RefundInfo getRefund(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        return RefundInfo.from(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundInfo> getRefundsByUser(UUID userId) {
        return refundRepository.findByUserId(userId).stream()
                .map(RefundInfo::from)
                .toList();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL` — `RefundApprovedEvent`, `ApplicationEventPublisher` import 모두 제거됨

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
refactor(payment): RefundService — Spring event 대신 outbox enqueue
```

---

## Phase 6: 정리 및 활성화

### Task 16: @EnableScheduling + @ConfigurationPropertiesScan 추가

**Files:**
- Modify: `payment-service/src/main/java/com/example/paymentservice/PaymentServiceApplication.java`

- [ ] **Step 1: 먼저 현재 파일 내용 확인**

Read [PaymentServiceApplication.java](payment-service/src/main/java/com/example/paymentservice/PaymentServiceApplication.java)

- [ ] **Step 2: `@EnableScheduling`이 없으면 추가, `OutboxProperties` 바인딩 활성화**

예시 (현재 구조에 맞춰 어노테이션만 추가):

```java
package com.example.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

> 주의: 기존에 `TossPaymentProperties`가 `@EnableConfigurationProperties(TossPaymentProperties.class)` 방식으로 바인딩되어 있다면 `@ConfigurationPropertiesScan` 대신 `@EnableConfigurationProperties({TossPaymentProperties.class, OutboxProperties.class})`로 맞춰 쓸 것. 프로젝트 기존 패턴을 따른다.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :payment-service:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: (사용자 수동) Commit 제안**

```
feat(payment): @EnableScheduling + OutboxProperties 바인딩 활성화
```

---

### Task 17: 레거시 삭제 — KafkaEventPublisher + Spring Event 클래스

**Files:**
- Delete: `payment-service/src/main/java/com/example/paymentservice/common/messaging/KafkaEventPublisher.java`
- Delete: `payment-service/src/main/java/com/example/paymentservice/payment/application/event/PaymentCompletedEvent.java`
- Delete: `payment-service/src/main/java/com/example/paymentservice/payment/application/event/PaymentFailedEvent.java`
- Delete: `payment-service/src/main/java/com/example/paymentservice/refund/application/event/RefundApprovedEvent.java`

- [ ] **Step 1: 각 파일 삭제 (에이전트는 파일 삭제 권한에 따라 rm 또는 사용자에게 알림)**

에이전트가 삭제할 때:

```
rm payment-service/src/main/java/com/example/paymentservice/common/messaging/KafkaEventPublisher.java
rm payment-service/src/main/java/com/example/paymentservice/payment/application/event/PaymentCompletedEvent.java
rm payment-service/src/main/java/com/example/paymentservice/payment/application/event/PaymentFailedEvent.java
rm payment-service/src/main/java/com/example/paymentservice/refund/application/event/RefundApprovedEvent.java
```

삭제 후 `event/` 폴더가 비었다면 폴더도 함께 삭제.

- [ ] **Step 2: 전체 빌드 + 테스트**

Run: `./gradlew :payment-service:build`
Expected: `BUILD SUCCESSFUL`, 모든 단위 테스트 통과

- [ ] **Step 3: (사용자 수동) Commit 제안**

```
refactor(payment): 레거시 Kafka 이벤트 리스너/Event 클래스 제거
```

---

### Task 18: 전체 빌드 + 수동 검증 체크리스트

**Files:** (변경 없음 — 검증)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew :payment-service:test`
Expected: `BUILD SUCCESSFUL`, 모든 테스트 통과 (기존 `PaymentServiceApplicationTests.contextLoads` 포함)

- [ ] **Step 2: 애플리케이션 부팅 스모크 테스트 (수동)**

로컬 환경(Postgres + Kafka)에서 기동:
```
./gradlew :payment-service:bootRun
```

- 콘솔에서 `outbox_messages` 테이블 생성 로그 확인 (`ddl-auto=update`)
- `OutboxRelay` 스케줄러 로그: 초기에 PENDING이 없을 때 Kafka 호출 없이 조용히 tick

- [ ] **Step 3: 수동 E2E 체크리스트**

프론트엔드 연동 후 아래를 수동 확인 (사용자 담당):

- [ ] 결제 성공 시:
  - `payments.status = SUCCESS`
  - `outbox_messages`에 `topic=payment.confirmed` row가 `PUBLISHED`로 남음
  - user-service가 Kafka 메시지 수신 → 쿠키 적립
- [ ] 결제 PG 실패 시:
  - `payments.status = FAILED`
  - `outbox_messages`에 `topic=payment.failed` row가 `PUBLISHED`로 남음
- [ ] 환불 승인 시:
  - `refunds.status = SUCCESS`
  - `outbox_messages`에 `topic=payment.refunded` row가 `PUBLISHED`로 남음
  - user-service가 쿠키 차감
- [ ] Kafka 다운 시나리오:
  - docker compose로 kafka 컨테이너 중단
  - 결제 시도 → DB는 SUCCESS, outbox는 `PENDING` 상태 유지, `retry_count`가 `>0`으로 증가
  - Kafka 재기동 → 자동으로 `PUBLISHED`로 전이되는지 로그 확인

- [ ] **Step 4: (사용자 수동) 최종 Commit + Push + PR**

로컬 모든 커밋을 `fix/payment/outbox` 브랜치로 정리 후:
```
git push -u origin fix/payment/outbox
gh pr create --base dev/payment --title "fix(payment): Transactional Outbox 패턴 적용" ...
```

PR 본문에는 다음 포함:
- 해결하는 문제 (dual write → Kafka 유실)
- 이번 PR에서 **하지 않는 것**: user-service consumer 멱등 처리 (별도 PR 필요)
- 테스트 결과 (단위 테스트 + 수동 E2E)
- 롤백 방법: 이 커밋들 revert하면 이전 `AFTER_COMMIT` Kafka 발행 구조로 복귀됨

---

## 자체 검수 (Self-Review 결과)

- **Spec coverage**: 설계서 §3(제안 아키텍처), §4(테이블), §5(코드 변경 범위), §6(재시도 정책), §7(Producer 설정), §8(동시성), §9(테스트)가 Task 1–18에 모두 매핑됨
- **Placeholder scan**: "적절히 처리", "TBD" 없음. 코드 블록은 전부 완성형
- **Type consistency**: `OutboxMessage.pending(aggregateType, aggregateId, topic, payload)` 시그니처가 Task 2, 10, 11, 14, 15에서 모두 일치. `OutboxRepository.findPendingForRelay(now, batchSize)` 시그니처 일치
- **용량**: 18개 태스크, 각 2-5분. 전체 소요 ≈ 1.5~2시간 (사용자 수동 검증 제외)

## 한눈에 보는 범위 제한 (재확인)

이번 PR은 **포함하지 않는다:**
- user-service consumer 멱등 처리 (`UserPaymentConsumer.paymentConfirmConsumer`에 `processed_messages` 체크 추가) → 별도 이슈/PR
- PG 성공 후 DB 반영 실패(문제 A) 복구 배치 → 후속 PR
- `outbox_messages` PUBLISHED 행 cleanup 잡 → 운영 투입 후
