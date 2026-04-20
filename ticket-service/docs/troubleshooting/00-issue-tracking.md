# ticket-service 이슈 트래킹

> 최종 갱신: 2026-04-17
> 대상 브랜치: `feature/tiket-self-payment-foundation`
> 총 27건 — RESOLVED 25, WONTFIX 2

## 범례

| 상태 | 의미 |
|------|------|
| RESOLVED | 해결 완료 |
| WONTFIX | 현재 스코프에서 수정하지 않음 |

---

## 전체 요약

| 분류 | 이슈 수 | RESOLVED | WONTFIX | 해결 문서 |
|------|---------|----------|---------|-----------|
| RES — 리소스·스레드 관리 | 8 | 8 | 0 | [01-resource-and-thread-management](./01-resource-and-thread-management.md) |
| TX — 트랜잭션·데이터 정합성 | 7 | 6 | 1 | [02-transaction-safety](./02-transaction-safety.md) |
| KFK — Kafka·메시징 안정성 | 5 | 5 | 0 | [03-kafka-messaging-stability](./03-kafka-messaging-stability.md) |
| PERF — 메모리·쿼리 성능 | 4 | 4 | 0 | [04-memory-and-query-performance](./04-memory-and-query-performance.md) |
| SCH — 스케줄링·배치 안전성 | 3 | 2 | 1 | [05-scheduling-and-batch-safety](./05-scheduling-and-batch-safety.md) |
| **합계** | **27** | **25** | **2** | |

---

## RES — 리소스·스레드 관리 (8건)

| ID | 심각도 | 상태 | 제목 | 파일 |
|----|--------|------|------|------|
| RES-001 | CRITICAL | RESOLVED | HikariCP 커넥션 풀 고갈 (QueueService 핫패스 DB 조회) | `QueueService.java` |
| RES-002 | CRITICAL | RESOLVED | `@Async` 스레드풀 미설정 (`SimpleAsyncTaskExecutor` fallback) | `QueueAutoProcessService.java` |
| RES-003 | CRITICAL | RESOLVED | `CompletableFuture.runAsync()` commonPool 사용 (병렬도=1) | `QueueAutoProcessService.java` |
| RES-004 | CRITICAL | RESOLVED | `@Async` queueExecutor 풀 고갈 → Kafka 드레인 전환 | `AsyncConfig.java`, `QueueDrainConsumer.java` |
| RES-005 | HIGH | RESOLVED | `UserClient` RestClient 타임아웃 미설정 | `UserConfig.java` |
| RES-006 | MEDIUM | RESOLVED | `AsyncConfig` RejectedExecutionHandler 미설정 | `AsyncConfig.java` |
| RES-007 | MEDIUM | RESOLVED | `AsyncConfig` Graceful Shutdown 미설정 | `AsyncConfig.java` |
| RES-008 | LOW | RESOLVED | `UserClient` 402 응답 미처리 → 500 반환 | `UserClient.java` |

## TX — 트랜잭션·데이터 정합성 (7건)

| ID | 심각도 | 상태 | 제목 | 파일 |
|----|--------|------|------|------|
| TX-001 | CRITICAL | RESOLVED | `TicketingStartService` Redis 키 잔류 (DB 롤백 후 Redis 열림) | `TicketingStartService.java` |
| TX-002 | CRITICAL | RESOLVED | `RefundService` 쿠키 이중 환불 (HTTP 후 DB 롤백) | `RefundService.java` |
| TX-003 | MEDIUM | RESOLVED | `SelfPaymentService` 잔여 동기 `checkAndProcess()` 호출 | `SelfPaymentService.java` |
| TX-004 | MEDIUM | RESOLVED | `CartService` Redis/DB 카운터 불일치 | `CartService.java` |
| TX-005 | CRITICAL | RESOLVED | `@Transactional` 내 HTTP 쿠키 차감 — 롤백 보상 부재 | `QueuePurchaseProcessor.java`, `SelfPaymentService.java` |
| TX-006 | HIGH | RESOLVED | `handleTicketRefunded()` 환불 HTTP 실패 시 후속 작업 전체 스킵 | `TicketEventListener.java` |
| TX-007 | MEDIUM | WONTFIX | `SelfPaymentService` 롤백 경로에서 Kafka 직접 발행 | `SelfPaymentService.java` |

## KFK — Kafka·메시징 안정성 (5건)

| ID | 심각도 | 상태 | 제목 | 파일 |
|----|--------|------|------|------|
| KFK-001 | CRITICAL | RESOLVED | `ScheduleEventConsumer` 중복 메시지 시 예외 → 재시도 무한 루프 | `ScheduleEventConsumer.java` |
| KFK-002 | HIGH | RESOLVED | `ScheduleEventConsumer` 전반적 에러 핸들링 부재 | `ScheduleEventConsumer.java` |
| KFK-003 | HIGH | RESOLVED | `QueueDrainConsumer` 예외 시 에러 핸들링 없음 | `QueueDrainConsumer.java` |
| KFK-004 | MEDIUM | RESOLVED | Kafka Topic 이름 중앙 관리 없음 (5개 파일 분산) | `KafkaTopics.java` (신규) |
| KFK-005 | MEDIUM | RESOLVED | `CartClosedEvent` userIds 미포함 → 다운스트림 알림 불가 | `CartClosedEvent.java`, `CartClosedMessage.java` |

## PERF — 메모리·쿼리 성능 (4건)

| ID | 심각도 | 상태 | 제목 | 파일 |
|----|--------|------|------|------|
| PERF-001 | HIGH | RESOLVED | `CartService.getMyCart()` N+1 쿼리 | `CartService.java` |
| PERF-002 | CRITICAL | RESOLVED | `TicketProvideBatchConfig` 전체 티켓 메모리 로딩 | `TicketProvideBatchConfig.java` |
| PERF-003 | MEDIUM | RESOLVED | `ReviewAuthService` 전체 티켓 메모리 로딩 | `ReviewAuthService.java` |
| PERF-004 | HIGH | RESOLVED | `drainQueue()` while(true) Redis 장애 시 무한 루프 위험 | `QueueAutoProcessService.java` |

## SCH — 스케줄링·배치 안전성 (3건)

| ID | 심각도 | 상태 | 제목 | 파일 |
|----|--------|------|------|------|
| SCH-001 | CRITICAL | RESOLVED | Streaming Start/Finish 상태 가드 누락 → Quartz 재시도 루프 | `StreamingStartService.java`, `StreamingFinishService.java` |
| SCH-002 | CRITICAL | WONTFIX | `@Scheduled` 클러스터 환경 중복 실행 (단일 인스턴스 운영) | `DailyTicketFeeProvideScheduler.java` |
| SCH-003 | MEDIUM | RESOLVED | `ticketCleanupBatchPort.run()` 실패 시 TICKETING 전이 진행 | `TicketingStartService.java` |

---

## 해결 이력 (시간순)

**1차 — HikariCP 고갈 대응:**
- RES-001: 핫패스 DB 조회 제거, Redis 캐싱 전환

**2차 — Gemini 코드 리뷰 대응:**
- RES-002 + RES-003: AsyncConfig 생성, queueExecutor 전용 풀
- SCH-001: Streaming 서비스 상태 가드 추가

**3차 — E2E 테스트 대응:**
- RES-004: @Async → Kafka 드레인 전환
- TX-001~TX-004: AFTER_COMMIT 패턴 전면 적용
- RES-008: UserClient 402 → flag=false 변환

**4차 — 이슈 트래킹 코드 검증:**
- KFK-001 + KFK-002: ScheduleEventConsumer 에러 핸들링
- RES-005: UserClient 타임아웃 (connect 3s, read 5s)
- PERF-001: CartService N+1 → findAllById 일괄 조회
- TX-006: handleTicketRefunded 독립 try-catch
- RES-006 + RES-007: AsyncConfig CallerRunsPolicy + graceful shutdown
- KFK-003: QueueDrainConsumer try-catch
- SCH-003: 배치 실패 시 전이 중단
- PERF-003: ReviewAuthService Slice 페이지네이션
- PERF-004: drainQueue for 루프 (MAX_DRAIN_ITERATIONS=500)

**5차 — MONITORING 이슈 해결:**
- KFK-004: KafkaTopics 상수 클래스 중앙화
- TX-005: TransactionSynchronization 롤백 보상
- PERF-002: 배치 Slice 페이지네이션
- KFK-005: CartClosedEvent userIds 필드 추가
- SCH-002: WONTFIX (단일 인스턴스)
- TX-007: WONTFIX (의도된 설계)

---

## 관련 문서

- 대기열 드레인 전략 → [`queue-drain-strategy`](../reference/flow/04-queue-drain-strategy.md)
- Kafka 토픽 레퍼런스 → [`kafka-topics-reference`](../reference/infra/03-kafka-topics-reference.md)
- Quartz Job 체이닝 → [`quartz-job-chaining`](../reference/infra/02-quartz-job-chaining.md)
- @TransactionalEventListener 패턴 → [`transactional-event-listener-pattern`](../reference/infra/04-transactional-event-listener-pattern.md)
- Redis 키 명세 → [`redis-keys-and-ttl`](../reference/infra/01-redis-keys-and-ttl.md)