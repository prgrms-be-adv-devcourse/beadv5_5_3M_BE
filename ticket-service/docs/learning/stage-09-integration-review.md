# Stage 9 — 백엔드 설계 관점 통합 회고

> **목표**: Stage 0~8에서 본 모든 조각을 **한 장짜리 설계 철학**으로 묶는다.
> 앞으로 이 서비스에서 기능을 추가하거나 장애를 디버깅할 때 기준이 되는 "이 코드베이스의 세계관".
> **예상 소요**: 0.5일

---

## 1. 설계 철학 한 줄 요약

> **"DB가 진실이고, Redis/Kafka/HTTP는 그 진실을 따라가는 사이드 이펙트. AFTER_COMMIT·보상 훅·원자 연산의 삼각축으로 불일치를 좁힌다."**

---

## 2. 3대 데이터 정합성 전략

### 2.1 `@TransactionalEventListener(AFTER_COMMIT)`
- **어디서**: `TicketEventListener`, `ScheduleEventListener` 전 핸들러
- **무엇을**: Redis 세팅, HTTP 환불, Kafka 발행, Quartz Job 등록
- **왜**: DB 커밋 전의 부수효과는 롤백 시 고아(orphan) 상태를 만듦. AFTER_COMMIT은 DB 커밋이 확정된 이후에만 실행되어 **"DB가 먼저, 사이드는 뒤"** 규칙을 강제.
- **한계**: 커밋 **이후** 사이드 실패는 원래 트랜잭션을 되돌릴 수 없음 → 리트라이/DLQ/보상 워커가 필요하지만 **이 서비스는 로그만 남김**. 모니터링·알람이 안전망.

### 2.2 `CookieCompensationHelper` — 보상 트랜잭션
- **어디서**: `SelfPaymentService`, `QueuePurchaseProcessor`
- **무엇을**: HTTP 차감 성공 후 DB 커밋 실패 시 쿠키 환불을 자동 실행
- **왜**: HTTP는 롤백할 수 없는 외부 상태. 대신 "반대 연산"을 준비해 두고 DB 롤백 신호(`STATUS_ROLLED_BACK`)를 트리거로 실행.
- **한계**: `afterCompletion` 안에서 환불 HTTP도 실패하면 수동 개입 필요. 현재 자동 재시도 워커 없음.

### 2.3 `@DisallowConcurrentExecution` + Kafka key 파티셔닝
- **어디서**: 5개 Quartz Job 전부 + `QueueDrainConsumer(concurrency=4, key=scheduleId)`
- **무엇을**: 동일 작업 단위의 동시 실행을 원천 차단
- **왜**: "같은 스케줄의 상태 전이를 두 번 돌리지 않기" · "같은 스케줄의 드레인을 직렬화"가 자기 자신의 invariant를 지키는 기본.
- **역할 차이**: `@DisallowConcurrentExecution`은 **JobDetail 단위**(서버 단일 or DB JobStore), Kafka key 파티셔닝은 **메시지 단위**(컨슈머 그룹 내 파티션 라우팅). 둘 다 "mutual exclusion"이지만 계층이 다름.

---

## 3. 3대 동시성 제어 도구

| 도구 | 어디서 | 무엇을 보장 |
|------|--------|-----------|
| Redis atomic INCR/DECR (DECR-first) | `collectWindow`, `QueueService.enter`, `CartService` | 단일 카운터에 대한 race-free 갱신 |
| Kafka key-based partitioning (scheduleId) | `queue.drain` 토픽 | 같은 scheduleId 메시지는 같은 파티션 = 같은 컨슈머 스레드 = 순차 처리 |
| Spring Batch 동기 실행 | `TicketCleanupBatch` from `TicketingStartService` | 정리 → remaining 계산 → 상태 전이 **원자성** (실패 시 전체 롤백) |

**중요**: 이 서비스는 **DB 락(SELECT FOR UPDATE)을 쓰지 않는다**. 비관적 락 대신 Redis 원자 연산으로 동시성을 해결 → 처리량 높음, 대신 "Redis가 Source of Truth인 순간"의 TTL·seed 관리가 중요해짐.

---

## 4. 서비스 간 통신 매트릭스

### Outgoing HTTP (ticket-service → 다른 서비스)
| 호출 | 엔드포인트 | 용도 |
|------|-----------|------|
| `UserClient.deductTicketFee` | user-service `POST /internal/users/deduct/cookie` | 결제 시 쿠키 차감 |
| `UserClient.refundCookie` | user-service `POST /internal/users/refund/cookie` | 환불 시 쿠키 환불 |

### Outgoing Kafka (ticket-service 발행)
| 토픽 | 시점 | 수신자 (추정) |
|------|------|--------------|
| `ticket.paid` | `TicketPaidEvent` AFTER_COMMIT | (현재 소비처 없음 — 분석용) |
| `ticket.refunded` | `TicketRefundedEvent` AFTER_COMMIT | (〃) |
| `cart.closed` | `CartClosedEvent` AFTER_COMMIT | (〃) |
| `ticketing.started` | `TicketingStartedEvent` AFTER_COMMIT | (〃) |
| `ticket.provide` | 일일 배치 1 AM | settlement-service |
| `ticket.review.authorized` | `ReviewAuthQuartzJob` | review-service |

### Incoming Kafka
| 토픽 | 처리 |
|------|------|
| `movie.schedule.confirmed` | `ScheduleEventConsumer` → Schedule 생성 + 5개 Quartz Job 등록 |

### Internal Kafka (self-produced, self-consumed)
| 토픽 | 발행 | 소비 | 용도 |
|------|------|------|------|
| `queue.drain` | 환불·결제 실패·재고 복구 시 | `QueueDrainConsumer` (concurrency=4) | 대기열 드레인 트리거 |
| `queue.terminated` | `QueueAutoProcessService.terminateQueue` | 외부 (현재 소비처 없음) | 대기열 종료 알림 |

---

## 5. 장애 모드와 복구 — 체크리스트

| 장애 대상 | 영향 | 현재 복구 |
|----------|------|----------|
| **PostgreSQL 다운** | 서비스 전체 정지 | 인프라 장애 — 외부 복구 대기 |
| **Redis 다운** | 큐·스톡·카트 카운트 전체 정지. TICKETING이 진행 중이면 구매 불가 | 인프라 복구 후 `TicketingStartService` 수동 재실행으로 키 재seed (현재 수동 프로세스) |
| **Kafka 다운** | 메시지 유실 (DLQ 없음). 내부 `queue.drain`은 다음 결제/환불 이벤트에서 재트리거 | 로그만 남음. 유실 건 수동 복구 |
| **user-service HTTP 다운** | `SelfPaymentService` 즉시 실패 (사용자에게 에러). 환불은 AFTER_COMMIT 리스너 catch → 로그만, 쿠키 미환불 | 수동 환불 조치 |
| **JVM 크래시** | `paying` 카운터 누수 (Stage 7 시나리오 E) | 다음 스케줄 시작 시 seed로 자동 복구 or TTL 만료 |
| **Quartz JobStore 락 유실** (prod clustered) | 클러스터 모드 깨지면 중복 실행 가능 | PostgreSQL 기반 락 의존 |

---

## 6. 놓치기 쉬운 백엔드 포인트 (심화 체크리스트)

학습자가 특히 놓치기 쉬운 것들 — 코드 리뷰할 때 일부러 의심하며 확인.

### 정합성
- ✔ **AFTER_COMMIT 이후 실패는 복구 안 됨** — 리스너의 3중 try-catch는 "실패해도 다음 작업은 진행"하는 의도적 설계지만, **그래서 Redis-DB-Kafka 삼자 불일치가 생길 수 있음**. 모니터링·알람이 안전망.
- ✔ **환불 시 `refundCookie` 실패 = 사용자 피해** — 수동 개입 없이는 쿠키 못 돌려받음. 재시도 워커 + 알람 설계 필요.
- ✔ **`CookieCompensationHelper`는 `afterCompletion` 내부 실패에 무방비** — 보상 HTTP 자체가 실패하면 log만 남음.

### 동시성
- ✔ **paying 카운터는 best-effort** — JVM 크래시 시 누수됨. TTL 의존.
- ✔ **`processWindowParallel` 윈도우 크기 무상한** — stock이 급격 복구되면 윈도우 폭주. 방어는 `queueExecutor` CallerRunsPolicy의 자연적 백프레셔뿐.
- ✔ **Kafka key=scheduleId 파티셔닝의 함의** — 컨슈머 수를 파티션 수보다 많게 늘려도 무의미 (같은 scheduleId는 한 컨슈머로 수렴).

### 이벤트 / Kafka
- ✔ **Kafka 메시지에 DLQ/재시도 없음** — 프로듀서 fire-and-forget, 컨슈머 실패는 Kafka 재시도 설정에 의존.
- ✔ **이벤트 페이로드는 서비스별로 각자 복제 정의** — 공유 모듈 없음. 토픽 스키마 변경 = 모든 consumer 동시 배포 위험.

### 스케줄러 / 배치
- ✔ **dev 환경 Quartz non-clustered** — 로컬 여러 인스턴스 띄우면 Job 중복 실행 가능. prod는 JobStore 클러스터링 필수.
- ✔ **`TicketCleanupBatchAdapter` 동기 실행** — 대량 스케줄 동시 진입 시 Quartz 스레드 장시간 점유. dev=5·prod=10 풀 주의.
- ✔ **`@Scheduled(cron)` 배치는 단일 인스턴스 전제** — 멀티 인스턴스 전환 시 ShedLock 또는 Quartz Clustered 필요 (SCH-002 현재 WONTFIX).

### 운영 / 보안
- ✔ **`JobTestController`** — presentation 레이어에 배치·Quartz Job을 수동 트리거하는 엔드포인트. 프로덕션 노출 시 위험. Gateway 경로 차단 or `@Profile("!prod")` 적용 검토.
- ✔ **`createrId`, `cookie` 등 Kafka 페이로드 필드 오타** — 컴파일러가 잡지 못함. 공유 스키마(avro·protobuf) 미도입으로 **개발자 합의로만 유지**.

---

## 7. 이 서비스의 "향후 확장 체크리스트"

이 서비스를 프로덕션에서 장기 운영하거나 규모를 키울 때 고려할 것들.

| 확장 축 | 현재 | 확장 시 할 일 |
|--------|------|--------------|
| 멀티 인스턴스 | 단일 | `@Scheduled` 대체 (ShedLock/Quartz clustered), Redis 락·서킷브레이커 도입 |
| 메시지 유실 방지 | 로그만 | DLQ 토픽 + 수동 재처리 엔드포인트 |
| 쿠키 보상 자동화 | 수동 | 보상 실패 건을 DB에 영속화 + 리트라이 워커 |
| 스키마 진화 | 자유 복제 | 공유 스키마 레지스트리(Avro/Confluent) or 공용 모듈 |
| 부하 분산 | scheduleId 1개로 집중 | scheduleId당 파티션 분산 설계 재검토 |
| 관측성 | 로그 위주 | 지표(paying 누수·queue 크기·DECR 실패 횟수) → Prometheus/Grafana |
| 보상 테스트 | E2E 해피 패스만 | 카오스 엔지니어링 도구로 HTTP·DB 장애 주입 테스트 |

---

## 8. Verification — 학습 완료 판정

다음 질문에 **모두 막힘 없이** 답할 수 있으면 이 서비스를 마스터한 것.

1. Ticket 하나가 RESERVED로 생성돼 환불되기까지의 전체 타임라인에서 **Redis 키 7개, Kafka 토픽 9개, HTTP 엔드포인트 2개**가 **각각 언제 건드려지는지** 그릴 수 있다.
2. Stage 7의 시나리오 A~E를 모두 설명할 수 있고, 각 시나리오의 **복구 메커니즘**을 코드 위치로 가리킬 수 있다.
3. `@TransactionalEventListener(AFTER_COMMIT)`를 `@EventListener`로 바꿨을 때 생기는 버그를 **3개 이상** 구체적으로 예시할 수 있다. (TX-001/002/004 유형)
4. `CookieCompensationHelper`가 등록되지 않는 코드 경로를 찾을 수 있고(예: SelfPayment의 잔액 부족 분기), 그 경로에서 어떤 보상이 일어나는지(또는 필요 없는지) 설명할 수 있다.
5. Quartz 클러스터링과 Kafka key 파티셔닝의 **역할 차이**를 설명할 수 있다. (둘 다 "동시 실행 방지"처럼 보이지만 다름)
6. 이 서비스에서 **DLQ가 없는** 지점 3개를 지적하고, 각 지점에 DLQ를 어떻게 추가할지 설계 스케치가 가능하다.

---

## 9. 선택적 실습 (심화 원하면)

### 9.1 로컬 전체 스택 기동
```bash
# 인프라
cd local/db    && docker-compose up -d
cd local/redis && docker-compose up -d

# ticket-service (dev)
cd ticket-service && ./gradlew bootRun
```

Swagger: `http://localhost:8084/swagger-ui.html`

### 9.2 Redis 키 변화 관찰
```bash
# 별도 터미널
docker exec -it <redis-container> redis-cli
> KEYS "*:schedule:*"
> MONITOR  # 실시간 명령 감시
```

E2E 시나리오 A 실행 중 Redis MONITOR를 켜면 DECR, INCR, ZPOPMIN이 어떻게 쏟아지는지 관찰 가능.

### 9.3 고의 장애 주입
- **user-service 내리기** → self-payment 호출 → `CookieCompensationHelper` 로그 확인 (→ 실제론 helper 호출 전 HTTP 실패 케이스)
- **Redis 내리기** → 큐 진입 시도 → 예외 경로 확인
- **Postgres 커넥션 풀 고갈** → DB 롤백 시 afterCompletion 훅 정상 동작 확인

---

## 10. 마무리 — 이 서비스에서 배운 "백엔드 설계 관점"

- **"트랜잭션 경계 = 일관성 경계"**. DB 커밋이 유일한 진실의 기준점. 그 전후로 모든 부수효과의 의미가 달라진다.
- **"원자 연산을 무기처럼 쓴다"**. DECR, ZPOPMIN, DEL의 반환값이 락 없는 상호배제를 구현하는 기본 도구.
- **"실패는 설계 요구사항이다"**. AFTER_COMMIT·보상 훅·3중 try-catch는 "실패를 복구하는" 게 아니라 "실패의 블라스트 반경을 좁히는" 도구.
- **"Kafka key = 동시성 단위"**. scheduleId를 key로 박는 순간 "같은 스케줄 = 같은 컨슈머 스레드"라는 자연 mutex가 생긴다.
- **"배치는 멱등해야 재시작 안전하다"**. `provideFlag=true`는 멱등, 그래서 일일 배치가 재시도 가능.
- **"운영 안전망이 설계의 일부"**. DLQ·재시도 워커가 없다면 **모니터링·알람·수동 런북**이 대안. 코드가 끝이 아니다.

---

## 원본 참고 (종합)

- `ticket-service/CLAUDE.md` — 공식 아키텍처 요약
- `docs/reference/domain/01-ticket-lifecycle.md`
- `docs/reference/flow/*` — 4개 플로우 문서
- `docs/reference/infra/*` — Redis / Quartz / Kafka / TransactionalEventListener
- `docs/troubleshooting/*` — TX-001~007, SCH-001~003, RES-001~004 등 실제 버그 사례

← 이전: [Stage 8 — 테스트 & E2E](stage-08-tests-e2e.md)
→ 처음으로: [README — 학습 순서](README.md)