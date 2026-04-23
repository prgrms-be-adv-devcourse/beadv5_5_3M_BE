# CLAUDE-ko.md (학습용 한국어 번역본)

> 이 파일은 `CLAUDE.md`(영문 원본, Claude Code가 자동 로드)의 **학습용 한국어 번역본**입니다.
> Claude Code 에이전트는 원본 `CLAUDE.md`를 참조하니, 이 파일은 **사람 학습 목적**으로만 유지하세요.

## 빌드 & 실행 명령어

Java 21, Spring Boot 4.0.4, Gradle. 각 서비스는 **독립적인 Gradle 프로젝트** — 모든 명령은 해당 서비스 디렉터리 안에서 실행합니다.

```bash
./gradlew build                                          # 빌드 + 테스트 실행
./gradlew build -x test                                  # 빌드만, 테스트 건너뜀
./gradlew test                                           # 전체 테스트 실행
./gradlew test --tests "com.example.ticketservice.*"     # 특정 테스트 클래스만 실행
./gradlew bootRun                                        # 로컬 실행 (기본 프로파일: dev)
./gradlew clean build                                    # 클린 후 재빌드
```

Docker 빌드는 `build/libs/`에 JAR가 있어야 합니다 — 먼저 `./gradlew build`, 그 다음 `docker build`. 모든 서비스가 동일한 Dockerfile 패턴을 사용: `eclipse-temurin:21-jre-jammy` 베이스 이미지.

Swagger UI (dev 프로파일 한정): `http://localhost:{port}/swagger-ui.html`

## 로컬 개발 환경 세팅

인프라 서비스는 `local/` 안의 docker-compose 파일로 실행합니다.

```bash
cd local/db    && docker-compose up -d   # PostgreSQL 18 + pgAdmin (포트 18080)
cd local/redis && docker-compose up -d   # Redis 7
cd local/mnio  && docker-compose up -d   # MinIO (S3 호환, user-service용)
```

PostgreSQL이 기동된 후, 레포 루트의 `init.sql`을 실행해 6개 서비스 DB를 생성합니다: `creator_db`, `payment_db`, `settlement_db`, `ticket_db`, `user_db`, `review_db`.

## 서비스 & 포트

| 서비스       | 포트 | 주요 의존성                  |
|--------------|------|-------------------------------|
| gateway      | 8000 | Spring Cloud Gateway, JWT     |
| creator      | 8080 | Redis 캐시                    |
| payment      | 8081 | Kafka, Toss Payments          |
| settlement   | 8083 | Kafka, HTTP → creator         |
| ticket       | 8084 | Redis, Kafka, Quartz, Batch   |
| user         | 8085 | Redis, Kafka, S3/MinIO        |
| movie        | 8086 | PostgreSQL, JPA               |
| review       | 8087 | PostgreSQL, JPA               |

**서비스 의존 관계:** Gateway는 모든 서비스로 라우팅. Settlement는 creator에 의존 (HTTP). Ticket은 user에 의존 (쿠키 차감/환불용 HTTP: `POST /internal/users/deduct/cookie`, `POST /internal/users/refund/cookie`).

## 아키텍처

모든 서비스가 **헥사고날 아키텍처(Hexagonal, Ports & Adapters)**를 동일한 4-패키지 레이아웃으로 사용합니다.

```
com.example.{service}/
  domain/          # 엔티티, Enum, 레포지토리 포트 인터페이스 — 프레임워크 의존성 없음
  application/     # 유스케이스 인터페이스, 서비스 구현, DTO, 이벤트(records),
                   # 출력 포트 인터페이스 (CachePort, EventPublisherPort, UserPort, …)
  infrastructure/  # 포트 어댑터: JPA 레포, Redis, Kafka, Quartz, RestClient, Batch
  presentation/    # REST 컨트롤러, GlobalExceptionHandler
```

### 레포지토리 3-계층 패턴 (모든 서비스 공통)

```
domain.repository.XxxRepository       (포트 인터페이스)
  ↑ 구현
infrastructure.persistence.impl.XxxRepositoryImpl
  ↑ 위임
infrastructure.persistence.XxxJpaRepository  (Spring Data JPA)
```

### 이벤트 드리븐 정합성

서비스는 트랜잭션 안에서 Spring `ApplicationEvent` 객체를 발행합니다. `infrastructure/event/` 안의 `@TransactionalEventListener(phase = AFTER_COMMIT)` 핸들러가 이를 Kafka로 전달 — **DB 트랜잭션 커밋 후에만 Kafka 메시지가 전송됨**을 보장합니다.

### Gateway 라우팅 & 인증

Gateway는 `AuthenticationFilter`로 JWT 토큰 검증을 수행합니다. 경로 기반 라우팅(`/api/creators/**`, `/api/payments/**` 등)으로 백엔드 서비스에 포워딩. 라우팅 대상은 `BASE_IP` + 서비스별 포트 환경변수로 설정. Gateway는 무상태 — DB나 Redis를 쓰지 않습니다.

### ticket-service 도메인 흐름 (가장 복잡한 서비스)

- **Schedule 생명주기:** `CART → IN_PROGRESSING → TICKETING → STREAMING → FINISH`
- **Ticket 생명주기:** `RESERVED → CONFIRMED`; 일일 배치 이후 `provideFlag=true`
- **장바구니 단계:** 사용자는 장바구니에 담음 (DB 레코드 + Redis 카운터 `cart:count:schedule:{id}`). `CartCloseQuartzJob`이 티켓팅 T-24h에 발동: 수요 ≤ 재고면 모든 카트에 대해 RESERVED 티켓 일괄 생성; 아니면 선착순 부분 예약.
- **티켓팅 단계:** `TicketingStartQuartzJob`이 미결제 RESERVED 티켓을 정리하고, Redis에 `stock:schedule:{id}`를 초기화하고, TICKETING으로 전이. 대기열은 sorted set `queue:schedule:{id}` 사용; `paying:schedule:{id}`가 동시 구매자 수를 추적.
- **자율결제:** `SelfPaymentService`를 통해 RESERVED → CONFIRMED; `UserPort`(user-service로 HTTP)로 쿠키 차감. HTTP 호출 후 DB 트랜잭션 실패 시 `CookieCompensationHelper`가 롤백 환불을 등록.
- **환불:** CONFIRMED 티켓 삭제; Redis의 재고 복구 + `UserPort`로 쿠키 복구.
- **대기열 드레인:** 재고가 복구되면(환불/결제 실패) `queue.drain` Kafka 메시지가 `QueueDrainConsumer`(concurrency=4)를 트리거해 대기 중인 사용자를 처리.
- **일일 배치:** `ticketProvideJob`이 새벽 1시에 실행 — CONFIRMED 티켓의 `provideFlag=true`를 일괄 세팅하고 `ticket.provide` 토픽으로 발행.

### ticket-service: 출력 포트 → 어댑터

| 포트 | 어댑터 |
|------|--------|
| `EventPublisherPort` | `KafkaEventPublisher` |
| `CachePort` | `RedisCacheAdapter` |
| `UserPort` | `UserClient` (RestClient) |
| `SchedulerPort` | `QuartzSchedulerAdapter` |
| `TicketProvideBatchPort` | `TicketProvideBatchAdapter` |
| `TicketCleanupBatchPort` | `TicketCleanupBatchAdapter` |

### ticket-service: Redis 키 규칙

| 키 | 용도 |
|-----|------|
| `cart:count:schedule:{id}` | CART 단계 수요 카운터 |
| `stock:schedule:{id}` | TICKETING 중 가용 좌석 수 |
| `queue:schedule:{id}` | 대기 사용자 sorted set (score = timestamp) |
| `paying:schedule:{id}` | 현재 구매 플로우에 있는 사용자 수 |
| `seats:schedule:{id}` | 총 좌석 수 (티켓팅 시작 시 캐싱) |
| `cookie:schedule:{id}` | 쿠키 단위 티켓 가격 (티켓팅 시작 시 캐싱) |
| `startTime:schedule:{id}` | 이벤트 시작 시각 ISO-8601 (대기열 TTL용) |

### ticket-service: Kafka 토픽

| 토픽 | 방향 | 목적 |
|------|------|------|
| `movie.schedule.confirmed` | 수신 | Schedule 엔티티 생성 |
| `ticket.paid` | 발신 | 자율결제 확정 |
| `ticket.refunded` | 발신 | 티켓 환불 완료 |
| `cart.closed` | 발신 | 장바구니 마감 (전체 or 부분) |
| `ticketing.started` | 발신 | 티켓팅 단계 시작 |
| `ticket.provide` | 발신 | 일일 배치 결제 알림 |
| `queue.drain` | 내부 | `QueueDrainConsumer`로 대기열 처리 트리거 |
| `queue.terminated` | 내부 | 대기열 종료 (재고 소진) |
| `ticket.review.authorized` | 발신 | 티켓 보유자 리뷰 권한 부여 |

### ticket-service: Quartz Job

| Job | 트리거 | 작업 |
|-----|--------|------|
| `CartCloseQuartzJob` | 티켓팅 T-24h | 장바구니 마감, RESERVED 일괄/부분 생성 |
| `TicketingStartQuartzJob` | `schedule.ticketingTime` | 정리, Redis에 재고 초기화, → TICKETING |
| `StreamingStartQuartzJob` | `schedule.startTime` | → STREAMING |
| `StreamingFinishQuartzJob` | `schedule.endTime` | → FINISH |
| `ReviewAuthQuartzJob` | `schedule.startTime` | 리뷰 권한 이벤트 발행 |

## 테스트

테스트는 **H2 in-memory DB**(`create-drop`)를 사용합니다. PostgreSQL이 아니므로 동작이 다를 수 있음. Kafka와 Batch 자동설정은 테스트 프로파일에서 제외됨(`src/test/resources/application.yaml`).

ticket-service에는 `e2e/` 아래에 Python E2E 테스트 스위트가 있습니다 (서비스 기동 필요).

## CI/CD

`.github/workflows/` 아래의 GitHub Actions 워크플로우:

- **CI** (`ci.yml`): 변경된 서비스를 감지하고 병렬로 빌드/테스트 (`fail-fast: false`). Docker Hub에 `{short-sha}` + `latest` 태그로 이미지 푸시.
- **CD** (`cd.yml`): `main`과 `dev/main` 브랜치에서만 실행. `docker compose --profile {service} up -d`를 SSH로 EC2에 배포.
- **PR 리뷰** (`pr-review.yml`): Gemini AI 코드 리뷰. 리뷰 규칙은 `.github/review-rules/` — CRITICAL 등급만 PR 코멘트로 표시.

## 프로파일

- **`dev`** (기본): 로컬 DB, DEBUG 로깅, Swagger 활성, Quartz 비클러스터(스레드 5개), `ddl-auto: create`
- **`prod`**: 모든 설정이 환경변수 기반, WARN 로깅, Swagger 비활성, Quartz 클러스터(스레드 10개), `ddl-auto: ${DDL_AUTO}`

주요 prod 환경변수: `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `CLIENT_USER_BASE_URL`, `SERVER_PORT`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`.

전체 환경변수는 `docs/env-guide.md` 참고, 기본값은 `.env.example`에서 확인.

## 배포

프로덕션은 EC2 위에서 프로파일 기반 docker-compose 사용. 각 서비스는 `docker compose --profile {service} up -d`로 독립 배포됨. PostgreSQL과 Redis는 EC2에서 직접 실행 (컨테이너 아님). 전체 배포 가이드는 `docs/DEPLOYMENT.md` 참고.

## 브랜치 & 커밋 컨벤션

**브랜치 구조:**
```
main                        # 프로덕션 — PR로만 머지
develop                     # 통합 브랜치; 서비스 간 테스트
dev/{service}               # 서비스별 CI/CD 트리거 (예: dev/ticket)
feature/{service}/{name}    # 기능 브랜치, dev/{service}에서 분기
fix/{service}/{name}        # 버그 수정
chore/{service}/{name}      # 설정/유지보수
```

**커밋 포맷:** `type(scope): message`
타입: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`
Scope = 서비스 이름 (예: `ticket`, `user`, `payment`)

## 에러 처리

각 서비스는 커스텀 예외(예: `TicketException`, `ScheduleException`)를 정의하고, 이는 `HttpStatus` + 메시지를 담은 `ErrorCode` Enum으로 뒷받침됩니다. `presentation` 계층의 `GlobalExceptionHandler`가 이를 REST 응답으로 매핑합니다.
