# ticket-service 문서 목차

## reference/domain — 도메인 모델

- [01-ticket-lifecycle](reference/domain/01-ticket-lifecycle.md) — 티켓 상태 전이 (RESERVED → CONFIRMED)
- [02-error-codes-reference](reference/domain/02-error-codes-reference.md) — TicketErrorCode, ScheduleErrorCode, QueueErrorCode 전체 목록

## reference/flow — 비즈니스 흐름

- [01-schedule-confirmed-to-ticketing-flow](reference/flow/01-schedule-confirmed-to-ticketing-flow.md) — Kafka 소비 → Quartz 등록 → 티켓팅까지 전체 타임라인
- [02-cart-reservation-flow](reference/flow/02-cart-reservation-flow.md) — 장바구니 추가/마감/Case A·B 분기/자율결제
- [03-queue-flow-and-implementation](reference/flow/03-queue-flow-and-implementation.md) — 대기열 진입, QueueDrainConsumer, 윈도우 병렬 처리
- [04-queue-drain-strategy](reference/flow/04-queue-drain-strategy.md) — 드레인 전략 상세 (collectWindow + processWindowParallel)

## reference/infra — 인프라 패턴

- [01-redis-keys-and-ttl](reference/infra/01-redis-keys-and-ttl.md) — Redis 키 목록, 용도, TTL, 장애 시나리오
- [02-quartz-job-chaining](reference/infra/02-quartz-job-chaining.md) — Quartz Job 5개 등록/취소, Job identity 네이밍
- [03-kafka-topics-reference](reference/infra/03-kafka-topics-reference.md) — 토픽별 발행/소비 상세, mermaid 다이어그램
- [04-transactional-event-listener-pattern](reference/infra/04-transactional-event-listener-pattern.md) — AFTER_COMMIT 패턴, CookieCompensationHelper, 이벤트 목록

## troubleshooting — 트러블슈팅

- [00-issue-tracking](troubleshooting/00-issue-tracking.md) — 이슈 분류 체계 (RES/TX/KFK/MEM/SCH)
- [01-resource-and-thread-management](troubleshooting/01-resource-and-thread-management.md) — HikariCP 풀 고갈, @Async→Kafka 전환, UserClient 타임아웃
- [02-transaction-safety](troubleshooting/02-transaction-safety.md) — AFTER_COMMIT 패턴 적용, 롤백 보상, CartUpdatedEvent
- [03-kafka-messaging-stability](troubleshooting/03-kafka-messaging-stability.md) — Kafka 메시징 안정성 이슈
- [04-memory-and-query-performance](troubleshooting/04-memory-and-query-performance.md) — 메모리, 쿼리 성능 이슈
- [05-scheduling-and-batch-safety](troubleshooting/05-scheduling-and-batch-safety.md) — Quartz/Batch 스케줄링 안전성

## study — 학습 노트

- [01-study-notes](study/01-study-notes.md) — Self-Invocation, setRollbackOnly, Redis 원자성, Kafka drain 전환, 헥사고날
- [02-quartz-usage-guide](study/02-quartz-usage-guide.md) — Quartz vs @Scheduled, JobDataMap, Job 그룹 구조, 트러블슈팅
