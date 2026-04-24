# Runbook

`streaming-service` 로컬 실행 절차 + 트러블슈팅. 새 개발자 10분 내 구동 목표.

---

## 1. 사전 준비

### 1.1 필수 설치

- **JDK 21** (Gradle 이 자동 다운로드하지만 시스템 JDK 있으면 편함)
- **Docker + docker-compose** (인프라 기동)
- **psql client** (검증용, 선택)
- **redis-cli** (검증용, 선택)
- **websocat** (WS 수동 테스트, 선택) — `cargo install websocat` 또는 release binary

### 1.2 로컬 인프라 (PostgreSQL + Redis)

```bash
# 모노레포 루트에 docker-compose 가 있으면 그걸로, 없으면 개별 실행
docker run -d --name pg -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:16

docker run -d --name redis -p 6379:6379 redis:7
```

### 1.3 DB 생성

```bash
psql -h localhost -U user -c "CREATE DATABASE streaming_db;"
# 기존 DB 들도 필요 (creator_db, user_db, ticket_db 등) — 각 서비스 RUNBOOK 참조
```

### 1.4 Kafka

`application-dev.yaml` 은 `.gitignore` 대상이므로 각자 환경에 맞춰 `spring.kafka.bootstrap-servers` 를 지정한다. 원격 공용 브로커를 쓰든 로컬을 쓰든 자유.

로컬 브로커 쓰려면:

```bash
docker run -d --name zookeeper -p 2181:2181 confluentinc/cp-zookeeper:latest
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=host.docker.internal:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  confluentinc/cp-kafka:latest

# 이 경우 각 서비스에 환경변수:
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### 1.5 ffmpeg (creator-service 용)

creator-service 가 업로드 검증 (`ffprobe`) + HLS 변환 (`ffmpeg`) 을 수행. streaming-service 는 쓰지 않음.

- Windows: `winget install Gyan.FFmpeg` 또는 [gyan.dev](https://www.gyan.dev/ffmpeg/builds/)
- macOS: `brew install ffmpeg`
- Linux: `apt-get install ffmpeg`

PATH 에 `ffmpeg` 와 `ffprobe` 둘 다 있어야 함.

---

## 2. 의존 서비스 구동 순서

streaming-service 는 **실제 upstream 서비스·Kafka 를 로컬에 구동하고 검증**하는 방식을 채택한다 (mock 쓰지 않음) — Kafka payload·HTTP 응답·Quartz 타이밍을 현실과 동일하게 검증하기 위함. 구동 순서:

| 순서 | 서비스 | 포트 | 확인 URL | 역할 |
|---|---|---|---|---|
| 1 | PostgreSQL | 5432 | `psql -c '\l'` | 모든 서비스 DB |
| 2 | Redis | 6379 | `redis-cli PING` | 세션 / viewer / rate limit |
| 3 | Kafka | 9092 | `kafka-topics --list` | 이벤트 |
| 4 | creator-service | 8080 | `curl http://localhost:8080/actuator/health` | HLS 업로드 · `/internal/movies/{id}/location` |
| 5 | user-service | 8085 | `curl http://localhost:8085/actuator/health` | 로그인 JWT |
| 6 | ticket-service | 8084 | `curl http://localhost:8084/actuator/health` | `ticket.review.authorized` 발행 |
| 7 | gateway-service | 8000 | `curl http://localhost:8000/actuator/health` | `/api/streaming/**`, `/ws/stream/**` 라우팅 |
| 8 | **streaming-service** | 8088 | `curl http://localhost:8088/actuator/health` | 본 서비스 |

### 2.1 외부 서비스 의존 체크

streaming-service 는 다음 외부 자원에 의존 — 기동 전 확인:

- **creator-service `/internal/movies/{id}/location`** — 세션 발급 시 `videoPath` lazy 조회. 없으면 `STREAM_LOCATION_UNAVAILABLE` (502). creator-service 가 HLS 파이프라인으로 기동 중이어야 하고 업로드된 영상의 산출물이 `storage.s3-path` 에 존재해야 함.
- **ticket-service `ReviewAuthQuartzJob`** — `startTime − 10m` 시점에 `ticket.review.authorized` 발행. 이 시점이 대기실 개방 시점과 같아야 함.
- **gateway-service 라우팅** — `/api/streaming/**`, `/ws/stream/**` 두 라우트가 `application-*.yaml` 에 있어야 gateway 경유 호출이 됨.

---

## 3. streaming-service 구동

### 3.1 dev 프로파일 (기본)

```bash
cd streaming-service
./gradlew bootRun
# 또는
./gradlew build && java -jar build/libs/streaming-service-*.jar
```

기대 로그:
- `Started StreamingServiceApplication in ... seconds`
- Quartz 스키마 초기화 (`initialize-schema: always`)
- Kafka consumer 등록 (토픽 2종)
- Redis 연결
- Tomcat @ 8088

### 3.2 prod 프로파일

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=...
export DB_USERNAME=...
export DB_PASSWORD=...
export DDL_AUTO=validate
export REDIS_HOST=...
export REDIS_PORT=6379
export SPRING_KAFKA_BOOTSTRAP_SERVERS=...
export SERVER_PORT=8088
export STREAMING_JWT_SECRET='...32+ bytes...'
export STREAMING_PUBLIC_BASE_URL=https://stream.example.com
export STORAGE_S3_PATH=/mnt/streaming
export CLIENT_CREATOR_BASE_URL=http://creator-service:8080
./gradlew bootRun
```

전체 env 목록: `CONFIG.md`.

### 3.3 단일 테스트 실행

```bash
./gradlew test                                              # 전체
./gradlew test --tests "ScheduleTest"                       # 단일 클래스
./gradlew test --tests "*.rewrites_manifest*"               # 패턴
```

---

## 4. Smoke Test

구동 후 즉시 수행 — Kafka 이벤트 주입 → DB 적재 → 세션 발급 경로를 골고루 확인.

**1) 스케줄 + Entitlement Kafka 주입** (`e2e/publish_events.py` 사용)

```bash
cd streaming-service/e2e
pip install kafka-python
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 python publish_events.py
# 기대: schedule + entitlement row 적재 확인
psql -h localhost -U user -d streaming_db -c "SELECT schedule_id FROM schedule;"
psql -h localhost -U user -d streaming_db -c "SELECT user_id, schedule_id FROM entitlement;"
```

**2) 세션 발급** (gateway 경유)

```bash
JWT=$(curl -s -X POST http://localhost:8085/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@test.com","password":"pw"}' | jq -r .accessToken)

curl -X POST http://localhost:8000/api/streaming/sessions \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"scheduleId": 42}'
# 기대: 200 + sessionToken, sessionId, manifestUrl, wsEndpoint
```

**3) WebSocket CONNECT** (`websocat` 또는 브라우저 DevTools)

```bash
# stomp 헤더에 token 포함
wscat -c ws://localhost:8088/ws/stream
# > CONNECT\naccept-version:1.2\ntoken:<sessionToken>\n\n\0
# 기대: CONNECTED 프레임
```

---

## 5. 트러블슈팅

### 5.1 Kafka broker 미접근

증상: 기동 시 `Connection to node -1 ... could not be established` 반복.

해결:
```bash
# 설정된 브로커 호스트/포트 확인 (application-dev.yaml 또는 env var)
# nc -zv <broker-host> <port>

# 차단 시 로컬 브로커 전환
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### 5.2 `streaming_db` 부재

증상: `FATAL: database "streaming_db" does not exist`.

해결:
```bash
psql -h localhost -U user -c "CREATE DATABASE streaming_db;"
```

### 5.3 Quartz 테이블 생성 실패

증상: `ERROR: relation "qrtz_locks" does not exist`.

해결: `spring.quartz.jdbc.initialize-schema=always` 세팅 확인 (dev yaml 기본값). prod 에서는 공식 Quartz SQL 스크립트로 수동 적용 (`org.quartz.impl.jdbcjobstore/tables_postgres.sql`).

### 5.4 WebSocket 연결 실패 (gateway 경유)

증상: gateway 에서 HTTP 업그레이드 요청이 404 반환.

해결: gateway 의 `/ws/stream/**` 라우트가 `application-*.yaml` 에 들어 있는지 확인. 직접 streaming-service (`ws://localhost:8088/ws/stream`) 로 연결해 보기 — 연결되면 gateway 라우트 문제.

### 5.5 HLS 재작성 실패 (빈 매니페스트 또는 404)

증상: `curl ... /api/streaming/42/index.m3u8?t=...` 가 404 또는 empty body.

해결:
- creator-service 가 HLS 파이프라인으로 기동 중인지 확인
- 업로드된 영상의 HLS 산출물이 실제 파일시스템(`storage.s3-path` 기본 `C:/Users/qorwh/s3`)에 존재하는지:
  ```bash
  ls $STORAGE_S3_PATH/movies/<uuid>/index.m3u8
  ```
- `schedule.video_path` 가 DB 에 세팅되어 있는지:
  ```bash
  psql -d streaming_db -c "SELECT schedule_id, video_path FROM schedule;"
  ```
  null 이면 `CreatorMovieLocationAdapter` 가 `/internal/movies/{id}/location` 호출 실패 — creator-service 로그 확인.

### 5.6 `INVALID_TOKEN` / `SESSION_EXPIRED` / `SESSION_MISMATCH`

- `INVALID_TOKEN`: 서명 실패. `streaming.jwt.secret` 가 발급 시점과 다른지 확인 (secret 교체 시 기존 토큰 전부 무효).
- `SESSION_EXPIRED`: JWT exp 지남. 새 세션 발급 필요.
- `SESSION_MISMATCH`: Redis 세션 키가 다른 sessionId 를 가리킴 (단일 세션 enforcement). 재발급.

### 5.7 Quartz Job 이 발동 안 함

- `QRTZ_TRIGGERS` 테이블에 해당 JobKey 확인:
  ```sql
  SELECT trigger_name, next_fire_time FROM qrtz_triggers WHERE job_name LIKE 'schedule-42-%';
  ```
- `next_fire_time` 이 과거면 MISFIRE — `org.quartz.scheduler.skipUpdateCheck` 및 MISFIRE policy 점검.

### 5.8 단일 세션 kick 가 안 전달됨

- 같은 JVM 내에서만 동작 (ADR 0002). 수동 테스트로 동일 streaming-service 인스턴스에 두 세션 붙여 검증.

---

## 6. 관련 문서

- `CONFIG.md` — 환경 변수 전체
- `API.md` — 엔드포인트 스펙
- `TROUBLESHOOTING.md` — 운영 중 자주 만나는 문제 모음
