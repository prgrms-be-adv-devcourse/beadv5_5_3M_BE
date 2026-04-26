# 에러 코드 레퍼런스

## TicketErrorCode

**예외 클래스:** `TicketException`
**발생 조건:** ticketId 기반 조회·상태 검증

| 코드 | HTTP | 메시지 | 발생 상황 |
|------|------|--------|----------|
| `NOT_FOUND` | 404 | 존재하지 않는 티켓입니다: {id} | ticketId로 조회 실패 |
| `NOT_RESERVED` | 409 | 예매 상태가 아닌 티켓입니다: {id} | `pay()` 호출 시 status != RESERVED |
| `NOT_YOUR_TICKET` | 403 | 본인의 티켓이 아닙니다: {id} | userId 불일치 |
| `INSUFFICIENT_BALANCE` | 402 | 쿠키 잔액이 부족합니다 (필요: {cookie}) | 쿠키 차감 실패 (`flag=false`) |
| `ALREADY_IN_CART` | 409 | 이미 장바구니에 담긴 스케줄입니다: {id} | 동일 스케줄 중복 cart 추가 |
| `NOT_CONFIRMED` | 409 | 확정(결제완료) 상태가 아닌 티켓입니다: {id} | `refund()` 호출 시 status != CONFIRMED |

---

## ScheduleErrorCode

**예외 클래스:** `ScheduleException`
**발생 조건:** scheduleId 기반 조회·상태 검증

| 코드 | HTTP | 메시지 | 발생 상황 |
|------|------|--------|----------|
| `NOT_FOUND` | 404 | 존재하지 않는 스케줄입니다: {id} | scheduleId로 조회 실패 |
| `FULL` | 409 | 티켓이 모두 매진된 스케줄입니다: {id} | 잔여 좌석 없음 |
| `EXPIRED` | 409 | 예매가 마감된 스케줄입니다: {id} | 예매 마감 후 시도 |
| `NOT_IN_CART_PERIOD` | 409 | 장바구니 기간이 아닌 스케줄입니다: {id} | status != CART 인 스케줄에 cart 추가 |
| `CART_CLOSED` | 409 | 장바구니가 마감된 스케줄입니다: {id} | CartCloseJob이 이미 실행된 스케줄 |
| `NOT_IN_TICKETING` | 409 | 티켓팅 기간이 아닌 스케줄입니다: {id} | status != TICKETING 인 스케줄에 대기열 진입 |
| `NOT_IN_STREAMING` | 409 | 스트리밍 중이 아닌 스케줄입니다: {id} | status != STREAMING 인 스케줄에 접근 |
| `INVALID_STATUS_FILTER` | 400 | 조회 가능한 status는 CART/IN_PROGRESSING/TICKETING만 허용됩니다. | `GET /api/tickets/schedules/open?status=...`에 STREAMING 또는 FINISH 포함 |

---

## QueueErrorCode

**예외 클래스:** `QueueException`
**발생 조건:** 대기열 진입 시 상태 검증

| 코드 | HTTP | 메시지 | 발생 상황 |
|------|------|--------|----------|
| `QUEUE_NOT_OPEN` | 409 | 대기열이 열려 있지 않은 스케줄입니다: {id} | `enter()` 시 Redis stock 키 미존재 |
| `ALREADY_IN_QUEUE` | 409 | 이미 대기열에 있는 유저입니다: {id} | ZADD 전 ZRANK 조회 결과 존재 |
| `SOLD_OUT` | 409 | 매진된 스케줄입니다: {id} | stock=0 AND paying=0 |

---

## API별 발생 가능 에러

### `POST /api/queue/{scheduleId}/enter`

```
QUEUE_NOT_OPEN       → Redis stock 키 미존재 (TICKETING 전 또는 TTL 만료)
INSUFFICIENT_BALANCE → 재고 있으나 쿠키 부족
ALREADY_IN_QUEUE     → 이미 대기열에 있음
SOLD_OUT             → stock=0 AND paying=0
```

### `POST /api/tickets/{ticketId}/pay`

```
NOT_FOUND            → 티켓 없음
NOT_YOUR_TICKET      → 본인 티켓 아님
NOT_RESERVED         → status != RESERVED
INSUFFICIENT_BALANCE → 쿠키 부족
```

### `POST /api/tickets/{ticketId}/refund`

```
NOT_FOUND            → 티켓 없음
NOT_YOUR_TICKET      → 본인 티켓 아님
NOT_CONFIRMED        → status != CONFIRMED
```

### `POST /api/cart/{scheduleId}`

```
NOT_FOUND            → 스케줄 없음
NOT_IN_CART_PERIOD   → status != CART
ALREADY_IN_CART      → 중복 추가
```

### `GET /api/tickets/schedules/open`

```
INVALID_STATUS_FILTER → status 쿼리 파라미터에 STREAMING 또는 FINISH 포함
```

---

## GlobalExceptionHandler 처리 대상

```java
@ExceptionHandler(TicketException.class)
@ExceptionHandler(ScheduleException.class)
@ExceptionHandler(QueueException.class)
```

모두 각 ErrorCode의 `HttpStatus`를 그대로 응답 코드로 사용.
