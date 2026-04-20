# Stage 8 — 테스트 & E2E

> **목표**: 이론으로 배운 상태 전이·동시성·보상 로직이 **실제로 어떻게 검증**되는지 보기.
> E2E 5개 시나리오가 어떤 불변식을 뒷받침하고, 통합 테스트의 H2·Kafka 배제로 인해 **놓치는 버그 유형**은 무엇인지.
> **예상 소요**: 1일

---

## 1. 테스트 지형도

| 종류 | 위치 | 실행 방법 | 범위 |
|------|------|-----------|------|
| 통합 테스트 | `src/test/java/com/example/ticketservice/` | `./gradlew test` | SpringBootTest, H2, Kafka/Batch autoConfig 배제 |
| E2E 시나리오 | `e2e/e2e_test.py` | `python e2e_test.py` | 실제 기동된 서비스 + PostgreSQL + Redis + Kafka 대상 |
| 테스트 데이터 | `e2e/test_users.sql`, `e2e/schedule_events.json` | — | 유저 5000명 + 스케줄 4건 |

**주목할 점**:
- 자바 통합 테스트는 현재 **`TicketServiceApplicationTests.contextLoads()` 단 하나**. 본격 검증은 Python E2E가 담당.
- 설계적 이유: 실제 버그(동시성·AFTER_COMMIT·HTTP·Kafka)는 H2·Mock 위에서 재현이 어렵고, 대신 실 인프라에서 시나리오로 검증하는 편이 신뢰도 높음.

---

## 2. 테스트 환경 — `src/test/resources/application.yaml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  kafka:
    bootstrap-servers: localhost:9092
  batch:
    jdbc:
      initialize-schema: never
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
      - org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
```

**배제된 것들**:
1. **Kafka** — `KafkaAutoConfiguration` 배제 → `KafkaEventPublisher`, `QueueDrainConsumer` 빈이 안 뜸. 컨텍스트 로딩만 검증.
2. **Batch** — `BatchAutoConfiguration` 배제 → `ticketProvideJob`, `JobOperator` 빈 없음.
3. **PostgreSQL** — H2로 교체. 네이티브 쿼리·JPQL 일부·벌크 UPDATE가 방언 차이로 예상 밖 동작할 수 있음.

---

## 3. E2E 시나리오 A~E — 각각이 검증하는 불변식

### 시나리오 A — Full Flow (scheduleId=1, seats=100, Case A)

50명 cart → cart close → **Case A bulk RESERVED 50장** → 30명 self-payment → ticketing start → 미결제 20장 삭제 → 60명 queue 구매 → 10명 대기 → 1명 환불 → 재고 복구 → auto drain.

**검증 불변식**:
- bulk 예약 후 50명 모두 RESERVED 티켓 1장씩 보유 (line 196)
- 이미 결제된 티켓 재결제 시 `409 CONFLICT` (line 223 — `TicketStatus != RESERVED` 가드)
- 타인 티켓 결제 시 `403 FORBIDDEN` (line 228)
- 티켓팅 시작 후 미결제 RESERVED 20장 삭제 확인 (line 243 — `TicketCleanupBatch`)
- 환불 후 해당 유저 티켓 없음 (line 294 — `handleTicketRefunded` 완결)

**주목**: A-4 "미결제 20장 삭제"는 **Stage 2의 `TicketingStartService` + Stage 6의 `TicketCleanupBatch`**가 동기 호출로 엮인 결과. 실패 시 TICKETING 전이 자체가 롤백된다는 Stage 6의 보호막이 여기서 실질 검증됨.

### 시나리오 B — Case B Flow (scheduleId=2, seats=3, demand=10)

10명 cart → cart close → **Case B: bulk 예약 없음** → ticketing start → 3명 queue 구매 → 4번째 유저는 `409 SOLD_OUT`.

**검증 불변식**:
- demand=10 >= seats=3 → bulk RESERVED **0장** (line 344 — `CartCloseService.java`의 demand/seats 분기)
- 3명 모두 `type=PURCHASED` (line 357 — 즉시 구매 성공)
- 4번째 `409 SOLD_OUT` → `paying=0, stock=0 → SOLD_OUT` 판정 (Stage 7 참고)

### 시나리오 C — SOLD_OUT (scheduleId=4, seats=2)

demand=0인 채 cart close (Case A, 수요 < seats이나 bulk 할당할 대상 없음) → ticketing start → 2석 전부 구매 → 3번째 유저 `409 SOLD_OUT`.

**검증 불변식**:
- seats=2, 2명 구매 후 stock=0 + 다른 paying 없음 → SOLD_OUT 판정 (Stage 7 paying 카운터 의존)

### 시나리오 D — INSUFFICIENT_BALANCE

`user4501`(balance=0) 대기열 진입 시 → `402 INSUFFICIENT_BALANCE` 기대.

**검증 불변식 (★ 핵심 보상 메커니즘)**:
- HTTP 차감 → DB save → HTTP 실패(`flag=false`) → `setRollbackOnly()` → 트랜잭션 롤백 → ticket INSERT 취소
- **실제로 이 경로에서 `CookieCompensationHelper`가 등록되지 않음** (response.flag()=false 분기에서 return → helper 호출 전). 대신 user-service가 **차감 자체를 하지 않았기 때문에** 보상 불필요.
- 만약 HTTP 차감 성공 후 DB 롤백이면 CookieCompensationHelper가 환불 콜백 수행 (Stage 7 시나리오 C와 연결).

**trap**: E2E 자체는 stock 선점 후 "차감 성공/실패"만 검증. CookieCompensationHelper가 호출되는 경로(차감 성공 후 DB 장애)는 **E2E로 재현하기 어려움** → 현실적으로 단위 테스트 공백.

### 시나리오 E — 500명 동시성 (scheduleId=3, seats=50)

`asyncio` + `aiohttp`로 500명 동시 큐 진입 → PURCHASED 정확히 50명, QUEUED 450명 예상.

**검증 불변식 (line 509, 530)**:
1. `PURCHASED count == 50` — DECR 원자성이 깨지면 51 이상 나올 수 있음 (오버부킹)
2. `duplicate tickets == 0` — 같은 유저가 두 장 받으면 unique constraint 위반인데, 테스트에서는 각자 1장씩 검증
3. stock ≥ 0 (음수 불가) — DECR-first 패턴 (Stage 7)이 보장

**500명 동시 진입이 실제로 검증하는 것**:
- Redis DECR 원자성: 500 요청이 동시에 DECR해도 서로 다른 값을 보며, 음수는 INCR 복구
- Kafka key=scheduleId 파티셔닝: `queue.drain` 메시지가 쏟아져도 같은 scheduleId는 동일 컨슈머 스레드에서 순차 처리 → window 수집 중 race 없음
- paying 카운터 정합성: 500건 중 50건 성공 후 paying=0, 나머지 450명 QUEUED 상태

---

## 4. 유저 데이터 설계 (`test_users.sql`)

```sql
-- 쿠키 분포 (티켓 가격 5000 기준):
--   user0001~user2000: balance = 100,000  (넉넉, 20회 구매 가능)
--   user2001~user3000: balance =  10,000  (2회 구매 가능)
--   user3001~user3500: balance =   5,000  (딱 1회, 두번째 실패)
--   user3501~user4000: balance =   3,000  (항상 부족)
--   user4001~user4500: balance =   1,000  (항상 부족)
--   user4501~user5000: balance =       0  (잔액 0)
-- 결제 실패 확률: 5000명 중 1500명(30%)이 잔액 부족
```

**의도**: 현실적인 분포 (다수는 구매 가능, 30%는 잔액 부족) → 동시성 테스트에서 **결제 실패 경로도 섞임** → 재고 복구 + 드레인 재트리거까지 자연스럽게 검증.

---

## 5. TX-001 ~ TX-007 실제 버그 사례 중 3개 해설

`docs/troubleshooting/02-transaction-safety.md`에서 발췌. 이 중 3개를 "어떤 race였고 어떻게 막았나"로 요약.

### TX-001 — TicketingStartService Redis 키 잔류

**Race**: `cachePort.setCounter(stock/...)` 실행 → DB `scheduleRepository.save(schedule)` 실패 → 롤백 → **Redis는 이미 stock 설정됨** → 대기열이 잘못 열림.

**Fix**: Redis 세팅을 `@TransactionalEventListener(AFTER_COMMIT)` 핸들러로 이동. DB 커밋 확정 이전엔 Redis 무변경.

### TX-002 — RefundService 이중 환불

**Race**: `userPort.refundCookie()` HTTP 성공 → `ticketRepository.delete()` DB 실패 → 롤백 → **쿠키 환불됨 + 티켓 CONFIRMED 잔존** → 사용자가 또 환불 버튼 누름 → 중복 환불.

**Fix**: HTTP 호출을 Service에서 제거, `TicketRefundedEvent` AFTER_COMMIT 리스너에서 수행. DB 롤백 시 이벤트 미발행 → HTTP 미실행.

### TX-005 — @Transactional 내 HTTP 쿠키 차감 롤백 보상

**Race**: `tryPurchase` 안에서 `userPort.deductTicketFee()` 성공 → `ticket.pay()` 이후 커밋 실패 → 쿠키 차감만 남음.

**Fix**: `CookieCompensationHelper.registerRollbackRefund()` — `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` 훅으로 쿠키 환불 HTTP를 자동 실행. (Stage 7 시나리오 C)

---

## 6. H2로 놓칠 수 있는 버그 유형

통합 테스트는 H2 in-memory DB를 쓰므로 **PostgreSQL-only** 동작이 노출되지 않는다.

### 6.1 네이티브 SQL / 방언 차이
- PostgreSQL의 `ON CONFLICT`, `RETURNING`, UUID 타입 핸들링
- JPA 쿼리라도 방언별 SQL 생성 차이 (예: `LIMIT vs TOP`)
- 이 서비스는 JPQL 기반이라 대부분 호환되지만 `bulkMarkProvided`처럼 벌크 UPDATE는 방언 영향 가능

### 6.2 인덱스·제약 동작 차이
- `uk_ticket_user_schedule` 유니크 제약 위반 시 예외 클래스(DataIntegrityViolationException) — 대부분 호환되지만 메시지 문자열에 의존하면 깨짐

### 6.3 트랜잭션 격리 수준
- H2 MVCC 기본 ≠ PostgreSQL READ COMMITTED. 팬텀 리드·직렬화 실패 등의 실제 현상은 재현 안 됨
- DECR·ZPOPMIN 같은 **Redis 원자 연산은 DB와 독립**이므로 이 차이는 큐 로직에 영향 없음

### 6.4 Kafka 파티셔닝 버그
- `KafkaAutoConfiguration` 배제 → 프로듀서/컨슈머 경로 실행 안 됨
- `queue.drain` key=scheduleId 파티션 라우팅, consumer lag, offset 커밋 실패 등 **Kafka 고유 이슈는 0% 통합 테스트 커버리지**
- 이를 보완하는 게 E2E 시나리오 E (동시성 500)

### 6.5 Batch chunk 경계 버그
- `BatchAutoConfiguration` 배제 → `ticketProvideJob` 동작 안 함
- `PAGE_SIZE=500` 경계에서 영속성 컨텍스트 초기화 타이밍, 쿼리 plan 변동 → **검증 수단 사실상 부재** (수동 운영으로 확인)

---

## 7. E2E 시나리오가 **검증하지 못하는** 것

| 항목 | E2E로 검증됨 | 미검증 |
|------|------------|--------|
| Case A bulk 생성 | ✅ 시나리오 A | — |
| Case B partial | ✅ 시나리오 B | — |
| 500-user concurrency | ✅ 시나리오 E | — |
| 잔액 부족 거절 | ✅ 시나리오 D | — |
| `CookieCompensationHelper` **HTTP 성공 후 DB 롤백** | ❌ | 네트워크 장애 주입 필요 |
| 환불 시 `refundCookie` **HTTP 실패** | ❌ | user-service mock 필요 |
| Kafka 프로듀서 **broker down** 시 메시지 유실 | ❌ | 의도적 DLQ 없음 |
| Quartz **misfire** 재실행 | ❌ | 시간 조작 필요 |
| `paying` 카운터 **JVM 크래시** 누수 | ❌ | JVM kill 주입 필요 |
| Batch 실패 시 TICKETING 전이 롤백 | ❌ | DB 장애 주입 필요 |

**의미**: E2E는 **해피 패스·정해진 실패 경로**만 검증. "보상 메커니즘이 제대로 동작하는지"는 대부분 **코드 리뷰 + 로그 모니터링**으로 보완.

---

## 8. 이 Stage의 "설계 교훈"

1. **"통합 테스트는 컨텍스트 로드만, 본격 검증은 E2E"** — 이 서비스가 선택한 전략. 기능 단위 단위 테스트보다 시나리오 기반 검증이 이 도메인(비동기·외부 의존)에 유리.
2. **E2E에는 "내부 테스트 컨트롤러"가 필요하다** — `/internal/test/jobs/cart-close/{id}` 같은 엔드포인트가 있음. 운영 배포 시 **접근 통제 필수** (현재 JobTestController 프로덕션 노출 시 위험).
3. **보상 메커니즘의 E2E 검증은 어렵다** — HTTP 성공 후 DB 장애 같은 시나리오는 카오스 엔지니어링 수준의 주입이 필요. 현재는 코드 리뷰에 의존.
4. **유저 데이터의 "실패 분포"도 테스트의 일부**: 30% 잔액 부족 분포가 "실패 경로도 자연스럽게 섞이는" 환경을 만든다.

---

## ★ 핵심 질문

1. ★ 시나리오 E가 검증하는 **4개 이상의 불변식**을 구체적으로 쓰라. (힌트: PURCHASED 정확 수, 중복 티켓 없음, stock 음수 없음, paying 누수 없음, …)
2. ★ TX-001·TX-002·TX-005 각각을 "race의 2줄 타임라인 + fix의 1줄"로 설명하라.
3. `refundCookie` **HTTP 실패** 시의 실 시스템 영향을 쓰고, 그걸 E2E로 재현하려면 어떤 mock/주입이 필요한지 답하라.
4. H2로 돌리는 통합 테스트에서 **놓칠 수 있는 버그 유형 3개**를 짚어라. 어떻게 보완하는 게 좋은가?
5. 시나리오 D가 `CookieCompensationHelper` 동작을 **직접 검증하지 못하는** 이유는? (힌트: user-service가 먼저 거절하면 helper가 등록되지 않음)
6. `JobTestController` 엔드포인트를 프로덕션에서 차단하려면? (답: `@Profile("!prod")`, gateway 레벨 차단, 경로 가드 등)

---

## 체크리스트

- [ ] 시나리오 A~E 각각이 검증하는 **핵심 불변식**을 1~2줄로 요약할 수 있다
- [ ] 시나리오 D가 helper 동작을 **간접** 검증일 뿐임을 이해했다
- [ ] H2의 한계 3가지(방언·Kafka·Batch)를 설명할 수 있다
- [ ] 통합 테스트가 컨텍스트 로드만 하는 이유(의도적 전략)를 설명할 수 있다
- [ ] TX-001·002·005를 "race→fix"로 요약할 수 있다

---

## 원본 참고

- `src/test/java/com/example/ticketservice/TicketServiceApplicationTests.java` — contextLoads()만
- `src/test/resources/application.yaml` — H2 + Kafka/Batch 배제 설정
- `e2e/e2e_test.py` — 5개 시나리오 (583줄)
- `e2e/schedule_events.json` — 4개 스케줄 테스트 데이터
- `e2e/test_users.sql` — 5000명 + 쿠키 잔액 분포
- `docs/troubleshooting/02-transaction-safety.md` — TX-001 ~ TX-007 전체

← 이전: [Stage 7 — 동시성 심화](stage-07-concurrency-deep.md)
→ 다음: [Stage 9 — 통합 회고](stage-09-integration-review.md)