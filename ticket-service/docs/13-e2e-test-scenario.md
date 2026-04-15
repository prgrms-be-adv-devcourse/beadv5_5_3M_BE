# E2E 수동 테스트 시나리오

## 사전 준비

### 서비스 기동 순서

```bash
# 1. PostgreSQL
cd beadv5_5_3M_BE/local/db && docker-compose up -d
# → localhost:5432, DB: ticket_db

# 2. Redis
docker run -d -p 6379:6379 redis:7
# → localhost:6379

# 3. Kafka
# → 52.78.88.98:9092 (외부 서버, 별도 기동 불필요)

# 4. User Service
# → localhost:8080 (직접 기동)

# 5. Ticket Service
cd ticket-service && ./gradlew bootRun
# → localhost:8084
```

### Swagger
```
http://localhost:8084/swagger-ui.html
```

### 공통 헤더
모든 `/api/*` 엔드포인트는 `X-User-Id: {UUID}` 헤더 필요.
테스트용 UUID 예시:
```
user-a: 00000000-0000-0000-0000-000000000001
user-b: 00000000-0000-0000-0000-000000000002
user-c: 00000000-0000-0000-0000-000000000003
user-d: 00000000-0000-0000-0000-000000000004
user-e: 00000000-0000-0000-0000-000000000005
```

---

## 시나리오 A — 전체 플로우 (Case A: 수요 < 재고)

수요(장바구니 수) < 재고(seats) 조건에서 가예약 → 자율결제 → 티켓팅 → 대기열 → 환불 전 구간 검증.

### A-0. 스케줄 생성

Kafka `movie.schedule.confirmed` 토픽에 메시지 발행:

```json
{
  "scheduleId": 1,
  "title": "테스트 공연",
  "startTime": "2026-05-01T19:00:00",
  "endTime": "2026-05-01T21:00:00",
  "ticketingTime": "2026-04-30T10:00:00",
  "cookie": 5000,
  "creatorId": "00000000-0000-0000-0000-000000000099",
  "movieId": 1,
  "imageUrl": "https://example.com/image.jpg",
  "seats": 3
}
```

**검증:**
```sql
SELECT id, title, status, seats FROM schedules WHERE id = 1;
-- status = CART, seats = 3

SELECT job_name, job_group FROM qrtz_job_details;
-- CartCloseJob_1 (CART_CLOSE 그룹)
-- TicketingStartJob_1 (TICKETING_START 그룹)
-- ReviewAuthJob_1 (REVIEW_AUTH 그룹)
```

---

### A-1. 장바구니 추가 (2명 — seats=3보다 적으므로 Case A)

```
POST /api/cart/1    X-User-Id: user-a
POST /api/cart/1    X-User-Id: user-b
```

**검증:**
```
GET /api/cart/1/count   → 2

SELECT COUNT(*) FROM carts WHERE schedule_id = 1;   -- 2
```

**오류 케이스:**
```
POST /api/cart/1    X-User-Id: user-a (중복)
→ 409 ALREADY_IN_CART
```

---

### A-2. 장바구니 마감 (CartCloseJob 수동 트리거)

```
POST /internal/test/jobs/cart-close/1
→ 200 OK
```

**검증:**
```sql
SELECT status FROM schedules WHERE id = 1;
-- IN_PROGRESSING

SELECT id, user_id, ticket_num, status
FROM tickets WHERE schedule_id = 1;
-- user-a, user-b 각각 RESERVED 티켓 1개씩 생성

SELECT COUNT(*) FROM carts WHERE schedule_id = 1;
-- 0 (전체 삭제됨)
```

**Kafka:** `cart.closed` 이벤트 발행 확인

---

### A-3. 자율결제

#### 성공 케이스 (user-a, 쿠키 충분)
```
POST /api/tickets/{ticketId_a}/pay    X-User-Id: user-a
→ 200 OK
```

```sql
SELECT status FROM tickets WHERE id = {ticketId_a};
-- CONFIRMED
```

**Kafka:** `ticket.paid` 이벤트 발행 확인

#### 실패 케이스 (user-b, 쿠키 부족)
user-b의 쿠키 잔액을 부족하게 설정 후:
```
POST /api/tickets/{ticketId_b}/pay    X-User-Id: user-b
→ 402 INSUFFICIENT_BALANCE

SELECT status FROM tickets WHERE id = {ticketId_b};
-- RESERVED (그대로)
```

---

### A-4. 티켓팅 시작 (TicketingStartJob 수동 트리거)

```
POST /internal/test/jobs/ticketing-start/1
→ 200 OK
```

**검증:**
```sql
SELECT status FROM schedules WHERE id = 1;
-- TICKETING

SELECT COUNT(*) FROM tickets WHERE schedule_id = 1 AND status = 'RESERVED';
-- 0 (미결제 RESERVED — user-b 티켓 삭제됨)
```

```bash
redis-cli GET stock:schedule:1
# 2  (seats=3, CONFIRMED=1 → 잔여=2)
```

**Kafka:** `ticketing.started` 이벤트 발행 확인

---

### A-5. 대기열 진입 — 즉시 구매 (stock > 0)

```
POST /api/queue/1/enter    X-User-Id: user-c
→ 200 OK
   { "type": "PURCHASED", "ticket": { ... } }
```

**검증:**
```bash
redis-cli GET stock:schedule:1   # 1

SELECT status FROM tickets WHERE user_id = 'user-c';   -- CONFIRMED
```

---

### A-6. 대기열 진입 — stock 소진 후 대기열 등록

```bash
# stock=1 남은 상태에서 소진
POST /api/queue/1/enter    X-User-Id: user-d
→ { "type": "PURCHASED" }   # stock=0

# 이후 진입 시 대기열 등록
POST /api/queue/1/enter    X-User-Id: user-e
→ { "type": "QUEUED", "position": 1 }

GET /api/queue/1/position    X-User-Id: user-e
→ { "position": 1 }
```

**검증:**
```bash
redis-cli ZRANGE queue:schedule:1 0 -1 WITHSCORES
# user-e <timestamp>
```

---

### A-7. 환불 → 대기열 자동 처리

```
POST /api/tickets/{ticketId_c}/refund    X-User-Id: user-c
→ 204 No Content
```

**검증 (환불 직후):**
```bash
redis-cli GET stock:schedule:1
# 잠시 1 → 0 (QueueAutoProcessService가 드레인하며 user-e 자동 구매)

redis-cli ZRANGE queue:schedule:1 0 -1
# (empty) — user-e가 대기열에서 빠짐
```

```sql
SELECT status FROM tickets WHERE user_id = 'user-e';
-- CONFIRMED (자동 구매 완료)
```

**Kafka:** `ticket.refunded` → `ticket.paid` 순서로 발행 확인

---

### A-8. 리뷰 권한 발행 (ReviewAuthJob 수동 트리거)

```
POST /internal/test/jobs/review-auth/1
→ 200 OK
```

**검증:**
```sql
SELECT user_id FROM tickets
WHERE schedule_id = 1 AND status = 'CONFIRMED';
-- 이 user_id들로 Kafka ticket.review.authorized 발행됨
```

---

## 시나리오 B — Case B (수요 ≥ 재고)

장바구니 수가 재고를 초과할 때 가예약 없이 모두 선착순 대기열로 진입하는 경로 검증.

```bash
# seats=2인 스케줄에 3명 장바구니 추가
POST /api/cart/{scheduleId}    X-User-Id: user-a
POST /api/cart/{scheduleId}    X-User-Id: user-b
POST /api/cart/{scheduleId}    X-User-Id: user-c

# 마감 트리거
POST /internal/test/jobs/cart-close/{scheduleId}
```

**검증:**
```sql
SELECT COUNT(*) FROM tickets
WHERE schedule_id = {scheduleId} AND status = 'RESERVED';
-- 0 (가예약 없음 — Case B 확인)

SELECT status FROM schedules WHERE id = {scheduleId};
-- IN_PROGRESSING
```

이후 티켓팅 시작 후 모든 유저가 동등하게 대기열 진입.

---

## 시나리오 C — SOLD_OUT 검증

재고도 0, 결제 중인 RESERVED도 없는 상태에서 진입 시도.

```bash
# 모든 stock 소진 + 대기 RESERVED=0 상태 만든 후
POST /api/queue/{scheduleId}/enter    X-User-Id: user-x
→ 409 SOLD_OUT
```

> INSUFFICIENT_BALANCE(402)가 아닌 SOLD_OUT(409)인지 반드시 확인.

---

## 시나리오 D — INSUFFICIENT_BALANCE (쿠키 부족)

stock > 0이지만 쿠키 잔액이 부족한 유저의 진입.

```bash
# 쿠키 잔액이 부족한 유저로 진입 시도 (user-service에서 잔액 0으로 설정)
POST /api/queue/{scheduleId}/enter    X-User-Id: user-broke
→ 402 INSUFFICIENT_BALANCE
```

**검증 (stock 복구 확인):**
```bash
redis-cli GET stock:schedule:{scheduleId}
# 진입 시도 전과 동일한 값 (DECR 후 INCR 복구됨)
```

> SOLD_OUT(409)이 아닌 INSUFFICIENT_BALANCE(402)인지 확인.

---

## 검증 포인트 체크리스트

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 1 | 스케줄 CART 상태 생성 | `SELECT status FROM schedules` | CART |
| 2 | Quartz Job 3개 등록 | `SELECT job_name FROM qrtz_job_details` | 3행 |
| 3 | 장바구니 카운터 | `GET /api/cart/{id}/count` | 추가한 수 |
| 4 | Case A: RESERVED 티켓 생성 | `SELECT status FROM tickets` | RESERVED |
| 5 | 장바구니 전체 삭제 | `SELECT COUNT(*) FROM carts` | 0 |
| 6 | 스케줄 → IN_PROGRESSING | `SELECT status FROM schedules` | IN_PROGRESSING |
| 7 | 자율결제 → CONFIRMED | `SELECT status FROM tickets` | CONFIRMED |
| 8 | 미결제 RESERVED 회수 | ticketing-start 후 RESERVED count | 0 |
| 9 | Redis stock 초기화 | `redis-cli GET stock:schedule:{id}` | seats - CONFIRMED 수 |
| 10 | 즉시 구매 (stock>0) | enter 응답 type | PURCHASED |
| 11 | 대기열 등록 (stock=0) | enter 응답 type | QUEUED |
| 12 | Redis queue ZSet 등록 | `redis-cli ZRANGE queue:...` | user UUID |
| 13 | 환불 후 stock 복구 | redis-cli GET stock | 복구 확인 |
| 14 | 대기열 자동 드레인 | 환불 후 대기 유저 상태 | CONFIRMED |
| 15 | 중복 Kafka 방지 | queue.terminated 발행 횟수 | 1회 |
| 16 | Case B: 가예약 없음 | RESERVED count | 0 |
| 17 | SOLD_OUT 에러 코드 | 응답 HTTP 상태 | 409 |
| 18 | INSUFFICIENT_BALANCE 에러 코드 | 응답 HTTP 상태 | 402 |
| 19 | stock 복구 (쿠키 부족) | redis-cli GET stock | 진입 전과 동일 |

---

## Redis 빠른 확인 명령어

```bash
# 재고
redis-cli GET stock:schedule:{scheduleId}

# 대기열 (score=입장시각 timestamp)
redis-cli ZRANGE queue:schedule:{scheduleId} 0 -1 WITHSCORES

# 장바구니 카운터
redis-cli GET cart:count:schedule:{scheduleId}
```

## Quartz DB 확인 쿼리

```sql
-- 등록된 Job 목록
SELECT job_name, job_group FROM qrtz_job_details;

-- Trigger 상태 및 다음 실행 시각
SELECT trigger_name, trigger_state,
       to_timestamp(next_fire_time / 1000) AS next_fire
FROM qrtz_triggers;
-- WAITING: 정상 대기 / COMPLETE: 실행 완료
```