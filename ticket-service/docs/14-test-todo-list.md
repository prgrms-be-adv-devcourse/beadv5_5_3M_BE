# 테스트 TODO List

> 상세 시나리오 및 검증 쿼리 → [`13-e2e-test-scenario.md`](./13-e2e-test-scenario.md)

---

## 사전 준비

### 서비스 기동
- [ ] PostgreSQL 기동 (`local/db/docker-compose up -d`)
- [ ] Redis 기동 (`docker run -d -p 6379:6379 redis:7`)
- [ ] User Service 기동 (localhost:8080)
- [ ] Ticket Service 기동 (`./gradlew bootRun`, localhost:8084)

### 유저 준비 (User Service에서 생성)

총 **6명** 필요:

| UUID | 이름 | 쿠키 잔액 | 용도 |
|------|------|----------|------|
| `00000000-0000-0000-0000-000000000001` | user-a | 충분 (10,000+) | 시나리오 A 장바구니 + 자율결제 |
| `00000000-0000-0000-0000-000000000002` | user-b | 부족 (0) | 자율결제 실패 테스트 |
| `00000000-0000-0000-0000-000000000003` | user-c | 충분 | 시나리오 A 즉시구매 + 환불 |
| `00000000-0000-0000-0000-000000000004` | user-d | 충분 | 시나리오 A stock 소진 |
| `00000000-0000-0000-0000-000000000005` | user-e | 충분 | 시나리오 A 대기열 진입 |
| `00000000-0000-0000-0000-000000000006` | user-f | 부족 (0) | 시나리오 D INSUFFICIENT_BALANCE |

### 스케줄 준비 (Kafka 발행)

총 **3개** 필요 (시나리오별 독립 상태 유지):

| scheduleId | seats | 용도 |
|-----------|-------|------|
| 1 | 3 | 시나리오 A — 전체 플로우 (Case A) |
| 2 | 2 | 시나리오 B — Case B (수요 ≥ 재고) |
| 3 | 2 | 시나리오 C/D — SOLD_OUT, INSUFFICIENT_BALANCE |

---

## 시나리오 A — 전체 플로우 (scheduleId=1, seats=3)

- [ ] A-0. Kafka `movie.schedule.confirmed` 발행 → status = CART
- [ ] A-0. Quartz Job 3개 등록 확인 (`qrtz_job_details`)
- [ ] A-1. user-a, user-b 장바구니 추가
- [ ] A-1. user-a 중복 추가 시 409 ALREADY_IN_CART 확인
- [ ] A-2. cart-close 트리거 → IN_PROGRESSING, RESERVED 티켓 2개 생성
- [ ] A-2. carts 테이블 전체 삭제 확인
- [ ] A-3. user-a 자율결제 성공 → CONFIRMED
- [ ] A-3. user-b 결제 시도 → 402 INSUFFICIENT_BALANCE, RESERVED 유지
- [ ] A-4. ticketing-start 트리거 → TICKETING, user-b 티켓 삭제
- [ ] A-4. `redis-cli GET stock:schedule:1` = 2 (seats=3, CONFIRMED=1)
- [ ] A-5. user-c 진입 → PURCHASED, stock=1
- [ ] A-6. user-d 진입 → PURCHASED, stock=0
- [ ] A-6. user-e 진입 → QUEUED, position=1
- [ ] A-6. Redis ZSet 등록 확인
- [ ] A-7. user-c 환불 → user-e 자동 CONFIRMED 전환
- [ ] A-7. Redis queue 비워짐 확인
- [ ] A-8. review-auth 트리거 → `ticket.review.authorized` 발행

---

## 시나리오 B — Case B (scheduleId=2, seats=2)

- [ ] user-a, user-b, user-c 장바구니 추가 (3명 > seats=2)
- [ ] cart-close → RESERVED 0개 확인 (가예약 없음)
- [ ] 스케줄 status = IN_PROGRESSING

---

## 시나리오 C — SOLD_OUT (scheduleId=3, seats=2)

- [ ] ticketing-start 트리거 → TICKETING, stock=2
- [ ] user-a, user-b 진입 → PURCHASED, stock=0
- [ ] user-c 진입 → 409 SOLD_OUT (402 아닌지 확인)

---

## 시나리오 D — INSUFFICIENT_BALANCE (scheduleId=3 재사용)

- [ ] stock > 0 상태에서 user-f(쿠키=0) 진입 → 402 INSUFFICIENT_BALANCE
- [ ] 409 SOLD_OUT이 아닌지 확인
- [ ] Redis stock 값 진입 전과 동일 확인 (복구됨)

---

## 추가 검증

- [ ] Quartz trigger_state = COMPLETE 확인 (수동 트리거 후)
- [ ] `queue.terminated` Kafka 이벤트 1회만 발행 (중복 없음)
- [ ] DB 롤백 시 Kafka/Redis 변경 없음 (`@TransactionalEventListener` 정합성)