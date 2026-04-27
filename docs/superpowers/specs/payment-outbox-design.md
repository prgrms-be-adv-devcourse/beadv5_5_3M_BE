# payment-service Transactional Outbox 설계서

- 작성일: 2026-04-22
- 작성자: y0000h
- 브랜치: `fix/payment/outbox` (베이스: `origin/dev/payment`, 머지 대상: `dev/payment`)
- 상태: Design Approved

## 1. 배경 (Why)

현재 `payment-service`는 결제 확정/환불 승인 처리에서 **DB 쓰기 + Kafka 발행**이라는 두 개의 외부 시스템 쓰기를 수행한다. 이 두 쓰기를 하나의 트랜잭션으로 묶을 수 없기 때문에 아래의 구멍(dual write 문제)이 존재한다.

### 1.1 현재 구조 요약

- `PaymentService.confirmPayment` — `TransactionTemplate`으로 TX1(생성) → PG 호출(TX 밖) → TX2(결과 반영) 분리. TX2 안에서 Spring `ApplicationEvent`를 발행.
- `RefundService.approveRefund` — 동일 패턴, `PROCESSING` 상태 사용.
- `KafkaEventPublisher` — `@TransactionalEventListener(AFTER_COMMIT)`로 Kafka 발행. **실패 시 error 로그만 남기고 종료**, 재시도/복구 없음.

### 1.2 식별된 장애 지점

| ID | 시점 | 현재 방어 | 결과 |
|----|------|-----------|------|
| **B (이번 설계의 주 타겟)** | DB 커밋 후 Kafka 발행 | 없음 (로그만) | DB=SUCCESS인데 `user-service`가 이벤트 수신 못 함 → **쿠키 미적립** 또는 **이중 자산**(환불 시) |
| A | TX2 DB 반영 자체 실패 | `retryCommit` 3회 | PG=성공, DB=`IN_PROGRESS`/`PROCESSING` 잔존 (이번 범위 외) |

이번 설계는 **문제 B를 원천 차단**하는 것을 목표로 한다.

## 2. 목표 / 비목표

### 목표
1. `payment-service`의 모든 비즈니스 이벤트(`payment.confirmed`, `payment.failed`, `payment.refunded`)가 **at-least-once** 로 Kafka에 전달되도록 보장한다.
2. Kafka 일시 장애, 네트워크 단절, JVM crash 등의 상황에서도 이벤트가 유실되지 않는다.
3. K8s 다중 replica 환경에서 동일 메시지의 중복 처리를 제거한다.

### 비목표 (Out of Scope)
1. `user-service` consumer의 멱등 처리 — **별도 PR**로 담당자가 처리
2. PG 성공 후 DB 반영 실패(문제 A) 복구 배치 — 후속 PR
3. `PUBLISHED` 상태 row cleanup 잡 — 운영 투입 후 데이터량 보고 결정
4. Debezium 등 CDC 도입 — 팀 운영 난이도 대비 효용 낮음. 필요 시 outbox 테이블 구조를 재사용해 후속 마이그레이션

## 3. 제안 아키텍처

```
[Controller] → [PaymentService / RefundService]
                    │
                    │ (동일 DB 트랜잭션)
                    ├─ UPDATE payments/refunds
                    └─ INSERT outbox_messages (status=PENDING)
                    │
                  [TX COMMIT]  ─── 원자적: 비즈니스 상태와 발행 의도가 함께 커밋
                    │
                    ▼
        ┌──────────────────────────────────┐
        │ OutboxRelay  @Scheduled(500ms)   │
        │                                  │
        │  SELECT ... WHERE status=PENDING │
        │    AND next_retry_at <= now()    │
        │  FOR UPDATE SKIP LOCKED          │
        │  LIMIT 100                       │
        │                                  │
        │  → kafkaTemplate.send(...).get() │
        │                                  │
        │  성공 → UPDATE status=PUBLISHED   │
        │  실패 → retry_count++,            │
        │         next_retry_at = backoff  │
        │         MAX 초과 → status=FAILED  │
        └──────────────────────────────────┘
                    │
                    ▼
                 [Kafka] → user-service consumer
```

### 3.1 원자성 보장 원리

`outbox_messages` 테이블이 같은 Postgres 스키마 안에 있으므로, 기존 `payments`/`refunds` 업데이트와 같은 트랜잭션에 포함된다. PG 호출 성공 후 TX2가 커밋되면 **비즈니스 상태 변경과 메시지 발행 의도가 동시에 영속화**된다. 이후 Kafka 실패는 outbox row가 DB에 남아있으므로 릴레이가 재시도한다.

### 3.2 Relay 선택 이유

- **앱 내 `@Scheduled` 폴링 릴레이** 채택
- Debezium CDC 미채택 이유: Kafka Connect 운영 포인트 추가, 팀의 장애 대응 경험 부재, 프로젝트 규모 대비 과도
- 향후 트래픽 증가 시 outbox 테이블 구조를 그대로 유지한 채 릴레이만 Debezium으로 교체 가능 (마이그레이션 경로 확보)

## 4. 데이터 모델

### 4.1 `outbox_messages` 테이블

```sql
CREATE TABLE outbox_messages (
    id             BIGSERIAL    PRIMARY KEY,
    message_id     UUID         NOT NULL UNIQUE,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP    NOT NULL,
    last_error     TEXT,
    created_at     TIMESTAMP    NOT NULL,
    published_at   TIMESTAMP
);

CREATE INDEX idx_outbox_pending
    ON outbox_messages(status, next_retry_at)
    WHERE status = 'PENDING';
```

### 4.2 컬럼 의미

| 컬럼 | 의미 |
|------|------|
| `message_id` | 메시지 고유 UUID. Kafka key 또는 header로 전달. 향후 consumer 멱등 키 |
| `aggregate_type` | `PAYMENT` 또는 `REFUND` |
| `aggregate_id` | `paymentId` 또는 `refundId`의 문자열 표현. Kafka 파티셔닝 키로 사용 |
| `topic` | `payment.confirmed` / `payment.failed` / `payment.refunded` |
| `payload` | JSON 직렬화된 `PaymentConfirmedMessage` / `PaymentRefundedMessage` / `PaymentFailedEvent` |
| `status` | `PENDING` / `PUBLISHED` / `FAILED` |
| `next_retry_at` | 다음 발행 시도 가능 시각. 최초는 `created_at`, 이후 exponential backoff |

### 4.3 상태 전이

```
PENDING ──success──▶ PUBLISHED
   │
   └──failure──▶ PENDING (retry_count++, next_retry_at += backoff)
                    │
                    └── retry_count ≥ MAX ──▶ FAILED
```

## 5. 코드 변경 범위

### 5.1 신규 파일

| 경로 | 역할 |
|------|------|
| `common/outbox/OutboxMessage.java` | JPA 엔티티 |
| `common/outbox/OutboxStatus.java` | enum |
| `common/outbox/OutboxRepository.java` (port) | 도메인 포트 |
| `common/outbox/infrastructure/OutboxJpaRepository.java` | Spring Data |
| `common/outbox/infrastructure/OutboxRepositoryAdapter.java` | 포트 구현 |
| `common/outbox/OutboxRelay.java` | `@Scheduled` 릴레이 |
| `common/outbox/OutboxPayloadSerializer.java` | `ObjectMapper` 래퍼 |

### 5.2 변경 파일

| 경로 | 변경 |
|------|------|
| `payment/application/PaymentService.java` | `retryCommit` 내부에서 `eventPublisher.publishEvent(PaymentCompletedEvent...)` → `outboxRepository.save(OutboxMessage.pending(...))`. `failPayment`도 동일하게 `payment.failed` outbox 저장 |
| `refund/application/RefundService.java` | `retryCommit` 내부에서 `RefundApprovedEvent` 스프링 이벤트 제거 → outbox 저장 |
| `PaymentServiceApplication.java` | `@EnableScheduling` 추가 |
| `application.yaml` | Kafka producer 설정 강화, 릴레이 파라미터(주기·배치 크기·MAX retries) 외부화 |
| 스키마 | 별도 DDL 파일 불필요. JPA 엔티티를 추가하면 `ddl-auto=update`(dev)로 자동 생성. Partial index는 `@Table(indexes=...)`로 표현 불가이므로 일반 인덱스로 선언하고, 필요 시 운영에서 수동 `CREATE INDEX ... WHERE` 실행 |

### 5.3 삭제 파일

| 경로 | 이유 |
|------|------|
| `common/messaging/KafkaEventPublisher.java` | `@TransactionalEventListener`로 Kafka 발행하던 역할이 `OutboxRelay`로 이관됨 |
| `payment/application/event/PaymentCompletedEvent.java` | Spring `ApplicationEvent` 더 이상 필요 없음 |
| `payment/application/event/PaymentFailedEvent.java` | 동일 |
| `refund/application/event/RefundApprovedEvent.java` | 동일 |

단, `common/messaging/dto/PaymentConfirmedMessage.java`, `PaymentRefundedMessage.java`, `PaymentTopics.java`는 **유지** — outbox payload 직렬화 및 토픽 상수로 계속 사용.

## 6. 재시도 / 실패 정책

| 항목 | 값 |
|------|-----|
| 릴레이 실행 주기 | 500ms (`fixedDelay`) |
| 배치 크기 | 100 rows / tick |
| `send().get()` 타임아웃 | 3s |
| 최대 재시도 횟수 | 5 |
| Backoff 수식 | `next_retry_at = now + (2^retry_count) × 10s` → 10s / 20s / 40s / 80s / 160s |
| 최대 시도 초과 시 | `status = FAILED` + ERROR 로그. row는 보존하여 운영 수동 확인 |

모든 수치는 `application.yaml`의 `payment.outbox.*` 설정으로 외부화하여 환경별 조정 가능.

## 7. Kafka Producer 설정

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 10
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        delivery.timeout.ms: 30000
        request.timeout.ms: 10000
```

- `enable.idempotence=true`: Producer 레벨에서 동일 메시지 중복 전송 제거
- `acks=all`: 모든 ISR 레플리카 확인 후 ack
- `max.in.flight ≤ 5`: idempotence 보장 전제 조건

## 8. 동시성 — K8s 다중 replica 대응

릴레이 쿼리에 `FOR UPDATE SKIP LOCKED`를 적용하여 여러 replica가 경합 없이 서로 다른 row를 처리한다. JPA Native Query 또는 `@Lock(LockModeType.PESSIMISTIC_WRITE)` + Postgres hint로 구현.

```java
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
```

## 9. 테스트 계획

### 9.1 단위 테스트
- `OutboxRelayTest`: Kafka mock으로 발행 성공/실패/MAX 초과 시나리오, backoff 계산 검증
- `PaymentServiceTest`: `confirmPayment` 성공 시 outbox row가 `PENDING`으로 저장되는지, topic과 payload가 올바른지 검증
- `RefundServiceTest`: `approveRefund` 동일

### 9.2 통합 테스트
- Testcontainers로 Postgres + Kafka 띄워서 전체 흐름 검증:
  - 결제 성공 → outbox insert → 릴레이 실행 → Kafka consumer 수신 확인 → `status=PUBLISHED`

### 9.3 수동 E2E (장애 시나리오)
- Kafka 컨테이너 중단 상태에서 결제 수행 → outbox에 `PENDING` 쌓이는지 확인 → Kafka 복구 후 자동 발행되는지 확인
- 의도적으로 잘못된 payload를 넣어 MAX 재시도 후 `FAILED` 전이 확인

## 10. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| 릴레이 `send()` 성공 후 `PUBLISHED` 마킹 전 앱 crash | 중복 발행 1회 가능 | `message_id` 생성 및 payload 포함 → 향후 `user-service`의 멱등 처리로 최종 해소 (별도 PR) |
| K8s multi-replica 경합 | 동일 row 중복 발행 | `FOR UPDATE SKIP LOCKED` |
| `outbox_messages` 무한 팽창 | 디스크/쿼리 성능 저하 | `PUBLISHED` cleanup 잡 (후속 PR, 현재는 관찰) |
| Producer 설정 변경 영향 | 지연 수 ms 증가 | 결제 흐름에서 허용 가능한 수준 |
| `FAILED` row 방치 | 메시지 영구 유실 | ERROR 로그 + 운영 모니터링 (대시보드 후속) |

## 11. 롤아웃 / 마이그레이션

- 본 프로젝트는 Flyway/Liquibase 미사용. JPA `ddl-auto=update`(dev)로 `outbox_messages` 테이블이 앱 기동 시 자동 생성됨
- 운영 환경(`ddl-auto=validate` 또는 `none`)은 사전에 DBA가 테이블/인덱스 생성 후 배포 필요

### 배포 순서
1. 로컬/dev: 앱 기동 시 `outbox_messages` 자동 생성 확인
2. 운영: 테이블 + 인덱스 수동 생성 스크립트 준비 → DBA 실행 → 애플리케이션 배포
3. 신규 결제/환불부터 outbox 경로 사용. 기존 진행 중인 결제 건 없음을 확인 (결제 플로우 특성상 TX2가 수 초 내 종료)
4. 로그 모니터링 (`PENDING` 누적, `FAILED` 발생 여부)
5. 정상 확인 후 `KafkaEventPublisher` 삭제에 의한 이벤트 유실이 없는지 최종 확인

## 12. 참고

- [microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) (향후 업그레이드 경로)
- 기존 트랜잭션 분리 설계 (dev/payment 머지된 `fix/payment/transaction-refactor` 브랜치 히스토리)
