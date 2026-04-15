# Quartz Job 수동 테스트 가이드

## 개요

`@Profile("dev")` 환경에서만 활성화되는 테스트용 API.
Quartz Job이 등록될 때까지 기다리지 않고 UseCase를 직접 호출해 로직을 즉시 검증할 수 있다.

**컨트롤러:** `JobTestController`
**베이스 URL:** `POST /internal/test/jobs`

---

## 전제 조건

테스트 전 DB에 Schedule이 존재해야 한다.
Kafka `movie.schedule.confirmed`를 발행하거나, 직접 DB에 삽입한다.

```sql
-- Schedule 상태 확인
SELECT id, title, status, ticketing_time, start_time, seats
FROM schedules
WHERE id = {scheduleId};
```

---

## 엔드포인트

### 1. 장바구니 마감

```
POST /internal/test/jobs/cart-close/{scheduleId}
```

**실행 조건:** `schedule.status = CART`

**실행 결과:**
- `cartCount < seats` → Case A: RESERVED 티켓 bulk 생성
- `cartCount >= seats` → Case B: 가예약 없음
- `schedule.status`: `CART → IN_PROGRESSING`
- Cart 레코드 전체 삭제
- Kafka `cart.closed` 발행

**검증 쿼리:**
```sql
SELECT status FROM schedules WHERE id = {scheduleId};
-- → IN_PROGRESSING

SELECT COUNT(*) FROM tickets WHERE schedule_id = {scheduleId} AND status = 'RESERVED';
-- Case A: cartCount 만큼 생성됨
```

---

### 2. 티켓팅 시작

```
POST /internal/test/jobs/ticketing-start/{scheduleId}
```

**실행 조건:** `schedule.status = IN_PROGRESSING`

**실행 결과:**
- 미결제 RESERVED 티켓 일괄 DELETE
- Redis `stock:schedule:{id}` 초기화 (`seats - CONFIRMED 수`)
- `schedule.status`: `IN_PROGRESSING → TICKETING`
- Kafka `ticketing.started` 발행

**검증:**
```sql
SELECT status FROM schedules WHERE id = {scheduleId};
-- → TICKETING

SELECT COUNT(*) FROM tickets WHERE schedule_id = {scheduleId} AND status = 'RESERVED';
-- → 0 (미결제 회수됨)
```

```bash
# Redis stock 확인
redis-cli GET stock:schedule:{scheduleId}
# → 잔여 좌석 수
```

---

### 3. 리뷰 권한 발행

```
POST /internal/test/jobs/review-auth/{scheduleId}
```

**실행 조건:** 없음 (상태 검증 없이 실행)

**실행 결과:**
- DB에서 `status = CONFIRMED` 티켓 목록 조회
- 각 userId에 Kafka `ticket.review.authorized` 발행

**검증 쿼리:**
```sql
SELECT user_id FROM tickets
WHERE schedule_id = {scheduleId} AND status = 'CONFIRMED';
-- 이 userId들로 Kafka 이벤트 발행됨
```

---

## 전체 흐름 테스트 순서

```
1. Kafka movie.schedule.confirmed 발행
   → Schedule 저장 (status: CART), Quartz Job 등록

2. 장바구니 추가 (선택)
   POST /api/cart/{scheduleId}  (X-User-Id 헤더 필요)

3. 장바구니 마감 트리거
   POST /internal/test/jobs/cart-close/{scheduleId}

4. (Case A) 자율결제
   POST /api/tickets/{ticketId}/pay  (X-User-Id 헤더 필요)

5. 티켓팅 시작 트리거
   POST /internal/test/jobs/ticketing-start/{scheduleId}

6. 대기열 진입 또는 즉시 구매
   POST /api/queue/{scheduleId}/enter  (X-User-Id 헤더 필요)

7. 환불 (선택)
   POST /api/tickets/{ticketId}/refund  (X-User-Id 헤더 필요)

8. 리뷰 권한 발행 트리거
   POST /internal/test/jobs/review-auth/{scheduleId}
```

---

## Swagger에서 사용

`http://localhost:8084/swagger-ui.html` 접속 후
**"Job Test (dev only)"** 태그에서 엔드포인트 확인 및 실행 가능.

---

## 주의사항

- `@Profile("dev")`이므로 **prod 환경에서는 빈 자체가 등록되지 않음**
- 각 UseCase는 내부적으로 schedule 상태를 검증하므로, 잘못된 순서로 호출하면 `ScheduleErrorCode` 예외 반환
- Redis가 실행 중이어야 `ticketing-start` 이후 stock 키가 정상 설정됨