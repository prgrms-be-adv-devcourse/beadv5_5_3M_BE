# Stage 0 — Orientation (전체 그림)

> **목표**: 코드에 들어가기 전에 "이 서비스는 뭐 하는 놈이고, 어떤 조각으로 이루어져 있나"를 머릿속에 그린다.
> **예상 소요**: 0.5일

---

## 1. 한 줄 요약

**ticket-service**는 "크리에이터의 **온라인 상영(스트리밍)** 이벤트"에 대해 **장바구니 → 자율결제 또는 선착순 대기열 → 결제 확정 → 환불 → 일일 정산"**까지의 티켓 라이프사이클을 전담하는 서비스다.

## 2. 핵심 용어 4개만 외우기

| 용어 | 의미 |
|------|------|
| **Schedule** | 상영 이벤트 단위. `CART → IN_PROGRESSING → TICKETING → STREAMING → FINISH` 5개 상태 |
| **Ticket** | 사용자 1명 × Schedule 1개 = 티켓 1장. `RESERVED → CONFIRMED` 2개 상태 |
| **Cart** | 티켓팅 24시간 전까지 수요 표현용. 마감 시 전부 삭제됨 |
| **쿠키(cookie)** | user-service가 관리하는 인앱 화폐. 티켓 가격 단위 |

## 3. 두 갈래 구매 흐름

티켓팅은 **수요 vs 재고**에 따라 둘 중 하나로 분기한다.

```
[장바구니 T-24h 마감 시점]
    │
    ├── 수요 ≤ 재고  →  Case A: 모두에게 RESERVED 자동 생성 → 자율결제 24h
    │
    └── 수요 >  재고  →  Case B: 아무에게도 생성 안 됨 → 티켓팅 시작 시 선착순 대기열
```

- **Case A (수요 적음)**: 사용자는 여유롭게 `SelfPaymentService.pay()`로 결제
- **Case B (수요 많음)**: 사용자는 `QueueService.enter()` → 대기열 진입 → 재고 생기면 자동 구매

## 4. 서비스 구성 요소 (인프라)

| 구성 요소 | 쓰임새 |
|-----------|--------|
| **PostgreSQL** | Schedule, Ticket, Cart 영속화 (`ticket_db`, 포트 5432) |
| **Redis** | 재고·대기열·카운터 (7개 키, 포트 6379) |
| **Kafka** | 서비스 간 이벤트 드리븐 통신 (9개 토픽) |
| **Quartz** | 시간 기반 작업 (5개 Job: CartClose, TicketingStart, StreamingStart/Finish, ReviewAuth) |
| **Spring Batch** | 일일 대량 처리 (`ticketProvideJob` 새벽 1시) |
| **user-service (HTTP)** | 쿠키 차감·환불 2개 엔드포인트 |

## 5. 아키텍처 — 헥사고날 한눈에

```
com.example.ticketservice/
  domain/          # 엔티티·Enum·레포지토리 인터페이스 — 프레임워크 무의존
  application/     # 유스케이스·서비스·이벤트(records)·출력 포트 인터페이스
  infrastructure/  # 포트 어댑터: JPA, Redis, Kafka, Quartz, RestClient, Batch, 이벤트 리스너
  presentation/    # REST 컨트롤러, GlobalExceptionHandler
  common/          # 예외, ErrorCode, PageResult
```

**핵심**: `application/` 은 `infrastructure/` 를 절대 모른다. 오직 **포트 인터페이스**에만 의존.

### 레포지토리 3-계층 패턴 (전 서비스 공통)

```
domain.repository.TicketRepository            ← 포트 인터페이스 (도메인)
  ↑ implements
infrastructure.persistence.impl.TicketRepositoryImpl  ← 도메인 모델↔JPA 엔티티 변환
  ↑ delegates to
infrastructure.persistence.TicketJpaRepository         ← Spring Data JPA
```

## 6. 이 서비스의 "설계 철학" 3가지 (지금은 느낌만)

1. **@TransactionalEventListener(AFTER_COMMIT)** — Redis/Kafka/HTTP 같은 부수효과는 **DB 커밋 후에만** 실행. 롤백 시 상태 불일치 방지.
2. **보상 트랜잭션(Compensating Transaction)** — HTTP는 롤백 불가. DB 커밋 실패 시 반대 HTTP로 되돌리는 `CookieCompensationHelper`.
3. **Kafka 파티션 키 = 동시성 단위** — `queue.drain` 토픽에 `scheduleId`를 key로 박아 "같은 스케줄의 드레인은 한 번에 하나만" 보장.

이 3가지는 Stage 2·4·7에서 각각 깊이 판다.

---

## ★ 핵심 질문 (스스로 답해 보기)

1. ★ **Schedule의 5개 상태 전이**(CART → IN_PROGRESSING → TICKETING → STREAMING → FINISH)를 누가, 언제, 무엇으로 트리거하는가?
2. ★ **Ticket의 2개 상태**(RESERVED → CONFIRMED)가 각각 의미하는 비즈니스 상태는?
3. **Case A vs Case B**는 어떤 조건으로 분기되며, 분기 시점은 언제인가? (힌트: 24h 전)
4. ticket-service가 **Redis, Kafka, user-service(HTTP), PostgreSQL** 중 하나씩 다운되었다 가정. 각각 어떤 기능이 망가지는가?
5. 왜 "application layer는 infrastructure를 절대 모른다"는 규칙이 필요한가? 이 규칙이 깨졌을 때 어떤 불편함이 생길까?
```
1. **Quartz 스케줄러**가 정해진 시간에 도달했을 때 자동으로 트리거
2. 결제 대기/결제완료
3. 수요 <= 재고/ 수요 >재고
4. 장애시 각각
 - **PostgreSQL:** 모든 읽기 쓰기가 마비되어 서비스 마비
 - **Redis:** 선착순 대기열과 실시간 재고 카운팅이 동작하지 않아 티켓팅 기능 마비
 - **Kafka:** 결제 후 티켓 상태 변경이나 대기열 다음 순번 호출이 이루어지지 않음
 - **user-service:** 티켓 확정이 불가
5. 필요성과 깨질 시 
 - infrastructure 내부가 변경되어도 application은 변경되지 않는다.
 - 기술 스텍 별경 시 비지니스 로직 수정 필요
 - 비지니스 로직 테스트 시 인프라 같이 띄워야함
```
---

## 다음 Stage로 가기 전 체크리스트

- [ ] Schedule 5-상태, Ticket 2-상태를 외웠다
- [ ] Case A / Case B 분기 조건을 설명할 수 있다
- [ ] Redis 7키, Kafka 9토픽, Quartz 5Job이 있다는 사실을 알고 있다 (지금은 개수만 기억)
- [ ] 헥사고날의 4-패키지 구조(domain/application/infrastructure/presentation)를 그릴 수 있다

---

## 원본 참고

- `beadv5_5_3M_BE/CLAUDE-ko.md` — monorepo 전체 개요
- `ticket-service/CLAUDE-ko.md` — ticket-service 심화
- `ticket-service/docs/README.md` — 전체 문서 인덱스
- `ticket-service/docs/reference/flow/01-schedule-confirmed-to-ticketing-flow.md` — 스케줄 전체 타임라인

→ 다음: [Stage 1 — 도메인 모델](stage-01-domain.md)