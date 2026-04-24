# 장바구니 예매 흐름

## 개요

유저는 공연 티켓팅 시작 24시간 전까지 장바구니에 담아 수요를 표현한다.
마감 시점(T-24h)에 수요 vs 재고를 비교해 **Case A** / **Case B**로 분기하여
이후 자율결제 또는 선착순 대기열로 이어진다.

---

## 장바구니 기간 (CART 상태)

```
[스케줄 확정] ~ [ticketingTime - 24h]
```

### 장바구니 추가 (`CartService.addToCart`)

```
조건: schedule.status == CART
중복: (user_id, schedule_id) UNIQUE → 이미 담은 경우 ALREADY_IN_CART 에러

DB: carts 테이블에 레코드 삽입
Redis: INCR cart:count:schedule:{scheduleId}
```

### 장바구니 제거 (`CartService.removeFromCart`)

```
DB: carts 테이블에서 레코드 삭제
Redis: DECR cart:count:schedule:{scheduleId}
```

### 수요 조회 (`CartService.getCartCount`)

```
Redis: GET cart:count:schedule:{scheduleId}
(DB 쿼리 없이 Redis counter로 빠르게 응답)
```

---

## 마감 분기 (T-24h: `CartCloseService`)

```
cartCount = Redis GET cart:count:schedule:{scheduleId}
seats     = schedule.seats

cartCount < seats  →  Case A
cartCount >= seats →  Case B
```

### Case A: 수요 < 재고 (전원 가예약)

```
1. cart에 담은 유저 목록 조회 (carts 테이블)
2. 각 유저에게 RESERVED 티켓 생성
   Ticket.createReserved(schedule, ticketNum, userId)
3. ticketRepository.saveAll(tickets)
4. schedule.closeCart() → CART → IN_PROGRESSING
5. Cart 레코드 전체 삭제 (deleteAllByScheduleId)
6. CartClosedEvent 발행 → AFTER_COMMIT:
   - Kafka: cart.closed (caseType="A", seats=cartCount)
```

각 유저는 24시간 유예기간 동안 자율결제(`POST /api/tickets/{id}/pay`)로 CONFIRMED 전환.
미결제 시 `TicketingStartJob` 시점에 일괄 DELETE.

### Case B: 수요 >= 재고 (선착순 모드)

```
1. 가예약 없음 (티켓 생성 안 함)
2. schedule.closeCart() → CART → IN_PROGRESSING
3. Cart 레코드 전체 삭제
4. CartClosedEvent 발행 → AFTER_COMMIT:
   - Kafka: cart.closed (caseType="B", seats=schedule.seats)
```

ticketingTime에 모든 유저가 동등하게 선착순 대기열(`POST /api/queue/{scheduleId}/enter`) 진입.

---

## Case A 자율결제 흐름

```
유저 → POST /api/tickets/{ticketId}/pay
  │
  ▼
SelfPaymentService.pay()
  │
  ├─ ticket.status == RESERVED 검증
  ├─ ticket.userId == 요청 userId 검증
  │
  ▼
UserPort.deductTicketFee(ticketId, cookie, userId)
  → HTTP POST /internal/users/deduct/cookie
  │
  ├─ flag=false (쿠키 부족)
  │    └─ stock 키 존재 시: INCR(stock) + Kafka `queue.drain` 발행
  │    └─ INSUFFICIENT_BALANCE 에러 반환
  │
  └─ flag=true (성공)
       └─ CookieCompensationHelper.registerRollbackRefund() (DB 롤백 시 쿠키 보상)
       └─ ticket.pay() → RESERVED → CONFIRMED
       └─ TicketPaidEvent 발행 → AFTER_COMMIT:
            - Kafka: ticket.paid
            - Kafka: queue.drain (대기열 드레인 트리거)
```

---

## 티켓 상태 전이 (Case A)

```
(없음)
  │ CartCloseService (Case A)
  ▼
RESERVED  ←→  자율결제 대기
  │ SelfPaymentService.pay()
  ▼
CONFIRMED

(미결제 시)
RESERVED
  │ TicketingStartService (일괄 DELETE)
  ▼
(삭제됨)
```

---

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/cart/{scheduleId}` | 장바구니 추가 |
| `DELETE` | `/api/cart/{scheduleId}` | 장바구니 제거 |
| `GET` | `/api/cart` | 내 장바구니 목록 |
| `GET` | `/api/cart/{scheduleId}/count` | 스케줄 수요 조회 |
| `POST` | `/api/tickets/{ticketId}/pay` | 자율결제 (Case A) |