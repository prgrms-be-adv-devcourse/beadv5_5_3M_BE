# TODO: 예치금 자율 결제 기반 티켓팅 (Self-Payment Model)

## Phase 1: 기반 작업
- [x] `ScheduleConfirmedMessage`에 `ticketingTime` 필드 추가 (선행 버그 수정)
- [x] `ScheduleEventConsumer`에서 `request.ticketingTime()` 전달
- [x] `build.gradle`에 `spring-boot-starter-quartz` 의존성 추가
- [x] `application.yaml` / `application-dev.yaml`에 Quartz JDBC JobStore 설정 추가
- [x] `Ticket.java` 변경:
  - `create(Schedule, int)` 제거 → `createReserved(Schedule, int ticketNum, UUID userId)` 추가 (RESERVED 상태로 직접 생성)
  - `pay()` 메서드 추가 (RESERVED → CONFIRMED)
  - `releaseUnpaid()` 불필요 (미결제 티켓은 DELETE)
  - `@Table` unique 제약 추가: `(user_id, schedule_id)`
- [x] `Schedule.java`에 `closeCart()`, `startTicketing()` 메서드 추가
- [x] `ScheduleEventConsumer`에서 티켓 생성 코드 제거 (Schedule 저장만, 티켓은 필요 시점에 생성)
- [x] `Cart.java` 엔티티 생성 (`carts` 테이블, `(user_id, schedule_id)` unique)
- [x] `CartRepository` 인터페이스 + `CartJpaRepository` + `CartRepositoryImpl` 생성
- [x] `CachePort`에 Counter/Set/ZSet 메서드 추가
- [x] `RedisCacheAdapter`에 위 메서드 구현
- [x] `SchedulerPort` 포트 인터페이스 생성 (Quartz 추상화)
- [x] `TicketCleanupBatchPort` 포트 인터페이스 생성
- [x] `QuartzSchedulerAdapter` 구현 (SchedulerPort)
- [x] `CartCloseQuartzJob` 구현 (T-24h 트리거)
- [x] `TicketingStartQuartzJob` 구현 (T-0 트리거)
- [x] `ReviewAuthQuartzJob` 구현 (startTime 트리거)
- [x] 도메인 이벤트 클래스 생성 (`TicketReservedEvent`, `TicketCancelledEvent`, `TicketPaidEvent`, `TicketRefundedEvent`, `CartUpdatedEvent`, `ScheduleInitializedEvent`)
- [x] `TicketEventListener` 구현 (@TransactionalEventListener AFTER_COMMIT → Kafka/Redis)
- [x] `ScheduleEventListener` 구현 (@TransactionalEventListener AFTER_COMMIT → Quartz 스케줄링, Cart DB 기반으로 Redis 초기화 불필요)
- [x] `ScheduleErrorCode`에 `NOT_IN_CART_PERIOD`, `CART_CLOSED`, `NOT_IN_TICKETING` 추가
- [x] `TicketErrorCode`에 `ALREADY_PAID`, `NOT_YOUR_TICKET`, `INSUFFICIENT_BALANCE`, `ALREADY_IN_CART` 추가
- [x] `QueueErrorCode` + `QueueException` 생성

## Phase 2: 리뷰 권한 시스템 리팩토링
- [ ] `TicketService`에서 Redis 리뷰 캐시 코드 제거 (`addToZSet`, `removeFromZSetByScore`)
- [ ] `TicketService`를 @TransactionalEventListener 패턴으로 전환
- [ ] `ConfirmScheduledTicketsService` 리팩토링 (Redis 의존 제거)
- [ ] `ReviewAuthorizationScheduler` 삭제 (Quartz로 대체)
- [ ] `ReviewAuthorizationCache` DTO 삭제
- [ ] `ReviewAuthUseCase` + `ReviewAuthService` 생성 (DB 직접 조회 → Kafka 발행)
- [ ] `TicketJpaRepository`에 `findAllByScheduleIdAndStatus` 추가
- [ ] `TicketRepository`에 대응 인터페이스 메서드 추가

## Phase 3: 장바구니 시스템
- [ ] `CartUseCase` 인터페이스 생성
- [ ] `CartService` 구현 (addToCart, removeFromCart, getMyCart, getCartCount) — Cart 엔티티(DB) 기반
- [ ] `CartController` 생성 (POST/DELETE/GET /api/cart)
- [ ] `CartItemResponse` DTO 생성
- [ ] `ScheduleEventListener` 구현 (AFTER_COMMIT → Quartz 3개 Job 등록만, Redis 초기화 불필요)

## Phase 4: 장바구니 마감 & 가예약
- [ ] `CartCloseUseCase` 인터페이스 생성
- [ ] `CartCloseService` 구현 (Case A: 수요 < 재고 → 전원 가예약 / Case B: 수요 >= 재고 → 선착순 모드)
- [ ] `TicketCleanupBatchConfig` 생성 (JPQL 미결제 RESERVED 티켓 일괄 DELETE)
- [ ] `TicketCleanupBatchAdapter` 구현 (TicketCleanupBatchPort)
- [ ] `TicketJpaRepository`에 `bulkReleaseUnpaid`, `countByScheduleIdAndStatus` 추가
- [ ] `CartClosedMessage` Kafka DTO 생성

## Phase 5: 자율 결제 (24시간 Grace Period)
- [ ] `SelfPaymentUseCase` 인터페이스 생성
- [ ] `SelfPaymentService` 구현 (RESERVED → CONFIRMED + 쿠키 차감)
- [ ] `POST /api/tickets/{ticketId}/pay` 엔드포인트 추가
- [ ] `TicketPaidMessage` Kafka DTO 생성

## Phase 6: 미결제 회수 & 선착순 대기열
- [ ] `TicketingStartUseCase` 인터페이스 생성
- [ ] `TicketingStartService` 구현 (미결제 회수 → Redis 재고 설정 → TICKETING 전환)
- [ ] `QueueUseCase` 인터페이스 생성
- [ ] `QueueService` 구현 (enterQueue, getQueuePosition, processQueuePurchase)
- [ ] `QueueController` 생성 (POST enter, GET position, POST purchase)
- [ ] `QueueEntryResponse`, `QueuePositionResponse` DTO 생성

## Phase 7: 환불 & 예외 처리
- [ ] `RefundUseCase` 인터페이스 생성
- [ ] `RefundService` 구현 (CONFIRMED → AVAILABLE + 재고 복구 + 쿠키 환불 이벤트)
- [ ] `POST /api/tickets/{ticketId}/refund` 엔드포인트 추가
- [ ] `TicketRefundedMessage` Kafka DTO 생성

## Phase 8: 통합 & 정리
- [ ] `GlobalExceptionHandler`에 `QueueException` 핸들러 추가
- [ ] Swagger 어노테이션 추가 (새 컨트롤러들)
- [ ] `TicketUseCase` + `TicketService` + `TicketController` 에서 `reserveTicket()` → `purchaseTicket(UUID userId, Long ticketId)` 리네임 및 파라미터 변경 (이미 RESERVED된 티켓 결제 → CONFIRMED)
- [ ] 전체 빌드 및 정합성 테스트