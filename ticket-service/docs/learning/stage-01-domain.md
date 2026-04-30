# Stage 1 — 도메인 모델

> **목표**: 엔티티·Enum에 **박혀 있는 비즈니스 규칙**을 코드로 직접 확인한다.
> 서비스 코드를 읽기 전에, 도메인이 자기 자신의 invariant를 어떻게 지키는지부터 본다.
> **예상 소요**: 0.5일

---

## 1. Ticket 엔티티 — 2-state 상태머신

### 핵심 발췌

`domain/model/Ticket.java`

```java
// 장바구니 마감(Case A) 또는 대기열 구매 시 RESERVED 상태로 직접 생성
public static Ticket createReserved(Schedule schedule, int ticketNum, UUID userId) {
    Ticket ticket = new Ticket();
    ticket.schedule = schedule;
    ticket.ticketNum = ticketNum;
    ticket.userId = userId;
    ticket.status = TicketStatus.RESERVED;
    ticket.provideFlag = false;
    return ticket;
}

// 자율결제 또는 대기열 구매 완료: RESERVED → CONFIRMED
public void pay() {
    if (this.status != TicketStatus.RESERVED) {
        throw TicketErrorCode.NOT_RESERVED.of(this.id);
    }
    this.status = TicketStatus.CONFIRMED;
}
```

**주목할 점**:
- 생성자가 `private` 수준이 아닌 `protected`지만, **정적 팩토리 메서드**로만 정상 생성됨 (`createReserved`).
- `pay()`는 **자기 상태를 먼저 검증**하고 전이. 외부 서비스가 규칙을 잊어도 엔티티가 막는다.
- `ticketNum`, `userId`, `schedule`, `provideFlag`는 한 번 세팅되면 수정 메서드가 없음 → 불변에 가깝다.

### 핵심 필드

| 필드 | 역할 |
|------|------|
| `status` | RESERVED / CONFIRMED |
| `ticketNum` | 좌석 번호 (1 ~ seats 사이 정수) |
| `userId` | UUID, 한 유저 × 한 스케줄 = 한 티켓 (`uk_ticket_user_schedule`) |
| `provideFlag` | 크리에이터에게 대금 지급 완료 여부. 일일 배치에서 true로 전환 |
| `schedule` | @ManyToOne, LAZY fetch — 불필요한 Schedule 로딩 방지 |

### DB 유니크 제약

```sql
UNIQUE KEY uk_ticket_user_schedule (user_id, schedule_id)
```

> 유저당 스케줄 1개 티켓만 존재 가능. RESERVED → 삭제 → 재구매는 레코드가 삭제되므로 가능.

---

## 2. Schedule 엔티티 — 5-state 상태머신

### 상태 전이 다이어그램

```
   [Schedule.create()]
         │
         ▼
       CART ──closeCart()──▶ IN_PROGRESSING ──startTicketing()──▶ TICKETING ──startStreaming()──▶ STREAMING ──finishStreaming()──▶ FINISH
```

### 핵심 발췌 — 각 전이는 **이전 상태를 검증**한 뒤에만 허용

`domain/model/Schedule.java`

```java
// 장바구니 마감: CART → IN_PROGRESSING
public void closeCart() {
    if (this.status != ScheduleStatus.CART) {
        throw ScheduleErrorCode.NOT_IN_CART_PERIOD.of(this.id);
    }
    this.status = ScheduleStatus.IN_PROGRESSING;
}

// 티켓팅 시작: IN_PROGRESSING → TICKETING
public void startTicketing() {
    if (this.status != ScheduleStatus.IN_PROGRESSING) {
        throw ScheduleErrorCode.CART_CLOSED.of(this.id);
    }
    this.status = ScheduleStatus.TICKETING;
}

// startStreaming(), finishStreaming() 도 동일 패턴 (상태 검증 → 전이)
```

**주목할 점**:
- 각 전이 메서드가 **가드 문장 + 한 줄 전이**로 짧다. 이게 모든 상태 머신의 정석.
- `setStatus(...)` 세터가 없다. 외부에서 임의 전이 불가.

### 핵심 필드

| 필드 | 역할 |
|------|------|
| `status` | 5개 상태 중 하나 |
| `seats` | 총 좌석 수 (immutable — 티켓팅 중 변경되지 않음) |
| `cookie` | 1장당 쿠키 가격 (immutable) |
| `startTime` | 스트리밍 시작 시각 |
| `endTime` | 스트리밍 종료 시각 |
| `ticketingTime` | 티켓팅 시작 시각 (여기서 -24h가 CartClose 시점) |
| `creatorId`, `movieId` | 크리에이터·영화 참조 |

### 왜 `seats`·`cookie`·`startTime`에 set 메서드가 없는가?

`TicketingStartService`가 티켓팅 시작 시점에 **Redis에 `seats`·`cookie`·`startTime` 값을 캐싱**한다. 이후 Redis 값을 SoT(Source of Truth)로 사용.
→ DB에서 관리자가 바꿔도 Redis는 모름 → **정합성 깨짐**.
→ 그래서 애초에 set 메서드가 없어 변경 불가.

---

## 3. Cart 엔티티 — 단순 조인 테이블

`domain/model/Cart.java`

```java
@Entity
@Table(name = "carts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cart_user_schedule", columnNames = {"user_id", "schedule_id"})
})
public class Cart {
    @Id @GeneratedValue private Long id;
    private UUID userId;
    private Long scheduleId;
    private LocalDateTime createdAt;

    public static Cart of(UUID userId, Long scheduleId) { ... }
}
```

**주목할 점**:
- `Schedule` 엔티티를 `@ManyToOne`으로 잡지 않고 **`scheduleId`(Long)** 만 저장. 느슨한 결합.
- 상태 필드 없음 — 단순 "(유저, 스케줄)" 조합일 뿐.
- `CartCloseQuartzJob`이 마감 후 **전부 삭제**. 이후 조회 안 됨.

---

## 4. Enum 2종 — 5줄짜리 핵심

`domain/enums/TicketStatus.java`

```java
public enum TicketStatus {
    RESERVED,    // 결제 대기 (Case A 가예약 / 대기열 구매 직후)
    CONFIRMED    // 결제 완료
}
```

`domain/enums/ScheduleStatus.java`

```java
public enum ScheduleStatus {
    CART, IN_PROGRESSING, TICKETING, STREAMING, FINISH
}
```

`domain/enums/SchedulePhase.java` (사용자 관점 단계, 외부 응답 격리용)

```java
public enum SchedulePhase {
    CART_PERIOD,           // ScheduleStatus.CART
    PROVISIONAL_PAYMENT,   // ScheduleStatus.IN_PROGRESSING (가결제기간)
    TICKETING_PERIOD;      // ScheduleStatus.TICKETING

    public static SchedulePhase from(ScheduleStatus status) { /* 1:1 매핑, STREAMING/FINISH는 IllegalArgumentException */ }
}
```

| Phase | ScheduleStatus | 의미 |
|-------|----------------|------|
| `CART_PERIOD` | `CART` | 장바구니 |
| `PROVISIONAL_PAYMENT` | `IN_PROGRESSING` | 가결제기간 (Case A 자율결제 대기) |
| `TICKETING_PERIOD` | `TICKETING` | 티켓팅기간 (선착순 큐/구매 오픈) |

내부 상태명(IN_PROGRESSING 등)을 외부 응답에 그대로 노출하지 않으려는 격리 enum. STREAMING/FINISH는 매핑 대상 아님 — 진입 가능한 3개 단계에만 한정. `GET /api/tickets/schedules/open` 응답의 `phase` 필드에 사용.

왜 Ticket이 2-state인가? → **"결제 대기"와 "결제 완료"만 구분하면 비즈니스상 충분**.
- "환불됨" 상태는 없음 → 환불은 **DELETE**로 처리.
- "사용됨"도 없음 → 스트리밍이 끝나면 자연스럽게 의미 소멸.

---

## 5. Repository 포트 (인터페이스만)

`domain/repository/TicketRepository.java` — **이름으로 의도를 읽는다**

```java
public interface TicketRepository {
    Ticket save(Ticket ticket);
    List<Ticket> saveAll(List<Ticket> tickets);                      // Case A 일괄 예약
    Optional<Ticket> findById(Long id);
    Page<Ticket> findAllByUserId(UUID userId, int page, int size);   // 내 티켓 목록 (페이지네이션)
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);  // 일일 배치에서 사용
    List<Ticket> findAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status);
    Slice<Ticket> findByScheduleIdAndStatus(Long scheduleId, TicketStatus status, int page, int size);

    int  deleteAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status);  // ★ 미결제 RESERVED 회수
    long countByScheduleIdAndStatus(Long scheduleId, TicketStatus status);      // ★ 티켓팅 시작 시 remaining 계산
    void delete(Ticket ticket);
}
```

**주목할 점**:
- `Page`는 totalCount 쿼리 포함, `Slice`는 hasNext만 — 대량 조회 시 성능 차이.
- `countByScheduleIdAndStatus`가 **`remaining = seats - CONFIRMED count`** 계산의 핵심 (Stage 2에서 사용).
- `deleteAllByScheduleIdAndStatus`는 미결제 회수 — 티켓팅 시작 시 대량 DELETE.

---

## 6. 이 Stage의 "설계 교훈"

**상태 머신을 엔티티 메서드에 위임하라.**
Service 레이어에 `if (ticket.getStatus() == RESERVED) ticket.setStatus(CONFIRMED)` 같은 코드가 한 번 생기면, 같은 전이 로직이 **여러 Service에 중복**되기 시작한다.
→ 엔티티가 자기 invariant를 지키면 `ticket.pay()` 한 줄만 쓰면 된다.

---

## ★ 핵심 질문

1. ★ `Ticket.pay()`의 상태 검증이 없다면 어떤 버그가 가능한가? 시나리오를 구체적으로 하나 들어 보라.
2. ★ 관리자 API로 TICKETING 중 `Schedule.seats`를 변경하면 어떤 정합성 문제가 생기는가? (힌트: Redis 캐싱)
3. `Cart` 엔티티에 상태 필드가 없는 이유는? 있다면 어떤 상태가 필요할까 상상해 보라.
4. `findAllByUserId`가 `Page`를 쓰고 `findByScheduleIdAndStatus`가 `Slice`를 쓰는 이유는? (힌트: 총 건수 쿼리)
5. Ticket이 `@ManyToOne(fetch = LAZY) Schedule`인 이유는? EAGER였다면?
```
1. 이미 결제된 티켓을 재결제 해 두번 별제가 일어날 수 있음
2. 티켓이 재고를 초과해서 확정되게나, 재고가 존재 함에도 팔지 못하는 상황이 생길 수 있음
3. 수요 조사용 임시 데이터 이고, 티켓팅 시작 시 삭제되기 때문에 필요하지 않음
4. `Page`, `Slice` 구분 이유
 - `findAllByUserId`는 유저가 내 티켓 목록에서 "내가 총 몇 장을 샀고, 총 몇 페이지가 있는지" 를 알아야 함
 - `findByScheduleIdAndStatus`는 시스템 내부에서 리뷰 권한 발행을 위해 쓰임으로 totalCount를 매번 계산하는것은 비효율 적임 다음데이터 있나 없나만 알면 됨
5. 필요한 데이터만 가져오고 나머지는 프록시 객체 형태로 가져와 불필요한 쿼리를 남발하지 않음 
   EAGER로 바뀔 경우 티켓을 조회 할 때 각 티켓 당 모든 스케줄의 정보를 매번 조회해 오는 N+1 문제 발생
```
---

## 체크리스트

- [ ] `Ticket.pay()` / `Schedule.closeCart()` 등 전이 메서드의 "가드 + 전이" 패턴을 외웠다
- [ ] Ticket의 유니크 제약(`user_id + schedule_id`)이 재구매 허용의 방법임을 이해했다
- [ ] `TicketRepository`에서 **Stage 2에서 자주 쓰일** 3개 메서드를 짚어낼 수 있다 (`saveAll`, `countByScheduleIdAndStatus`, `deleteAllByScheduleIdAndStatus`)
- [ ] Schedule에 `seats`·`cookie` set 메서드가 없는 이유를 설명할 수 있다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/domain/model/Ticket.java`
- `src/main/java/com/example/ticketservice/domain/model/Schedule.java`
- `src/main/java/com/example/ticketservice/domain/model/Cart.java`
- `src/main/java/com/example/ticketservice/domain/enums/TicketStatus.java`
- `src/main/java/com/example/ticketservice/domain/enums/ScheduleStatus.java`
- `src/main/java/com/example/ticketservice/domain/enums/SchedulePhase.java`
- `src/main/java/com/example/ticketservice/domain/repository/TicketRepository.java`
- `docs/reference/domain/01-ticket-lifecycle.md` — 공식 라이프사이클 문서
- `docs/reference/domain/02-error-codes-reference.md` — `NOT_RESERVED`, `CART_CLOSED` 등 에러 코드

← 이전: [Stage 0 — Orientation](stage-00-orientation.md)
→ 다음: [Stage 2 — 애플리케이션 서비스](stage-02-application-services.md)