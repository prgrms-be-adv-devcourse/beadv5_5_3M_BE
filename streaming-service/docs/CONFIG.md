# Configuration Reference

`streaming-service` 의 `application*.yaml` 에서 사용하는 모든 property. dev/prod 기본값·환경변수·허용 범위를 정리한다.

`application-dev.yaml` 은 `.gitignore` 대상 — 개인 환경에 맞춰 자유롭게 수정. `application-prod.yaml` 은 전부 환경변수 주입.

---

## 1. Spring 코어

| Key | dev 기본 | prod 기본 | env var | 비고 |
|---|---|---|---|---|
| `spring.application.name` | `streaming-service` | 동일 | - | 로깅·모니터링 태그 |
| `spring.profiles.active` | `dev` (application.yaml) | `prod` | `SPRING_PROFILES_ACTIVE` | |
| `spring.threads.virtual.enabled` | `true` | `true` | - | Java 21 가상 스레드. HLS 블로킹 I/O 를 가상 스레드로 처리 |
| `spring.servlet.multipart.max-file-size` | `500MB` | (미설정) | - | 이 서비스는 업로드 안 받지만 기본값 유지 |

---

## 2. 데이터소스 (PostgreSQL)

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/streaming_db` | `jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME:streaming_db}` | `DB_HOST`, `DB_PORT`, `DB_NAME` |
| `spring.datasource.username` | `user` | `${DB_USERNAME}` | `DB_USERNAME` |
| `spring.datasource.password` | `password` | `${DB_PASSWORD}` | `DB_PASSWORD` |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | 동일 | - |
| `spring.datasource.hikari.maximum-pool-size` | (기본값 10) | `15` | - |
| `spring.datasource.hikari.minimum-idle` | (기본값) | `5` | - |

### JPA

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `create` | `${DDL_AUTO:validate}` | `DDL_AUTO` (권장 `validate`) |
| `spring.jpa.properties.hibernate.dialect` | `org.hibernate.dialect.PostgreSQLDialect` | 동일 | - |
| `spring.jpa.properties.hibernate.format_sql` | `true` | (미설정) | - |
| `spring.jpa.show-sql` | `false` | (미설정) | - |

---

## 3. Kafka

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `spring.kafka.bootstrap-servers` | (각자 설정, `application-dev.yaml` 은 gitignored) | `${KAFKA_BOOTSTRAP_SERVERS}` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `spring.kafka.consumer.group-id` | `streaming-service-dev` | `${KAFKA_CONSUMER_GROUP_ID:streaming-service}` | `KAFKA_CONSUMER_GROUP_ID` |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | `earliest` | - |
| `spring.kafka.consumer.key-deserializer` | `StringDeserializer` | 동일 | - |
| `spring.kafka.consumer.value-deserializer` | `StringDeserializer` | 동일 | - |
| `spring.kafka.producer.key-serializer` | `StringSerializer` | 동일 | - |
| `spring.kafka.producer.value-serializer` | `StringSerializer` | 동일 | - |

> 컨슈머·프로듀서 모두 `String` 기반. JSON 파싱은 `infrastructure/util/KafkaMessageUtil` 이 담당 (직접 ObjectMapper) — Spring Kafka `JsonDeserializer` 는 쓰지 않는다. payload DTO 는 `MESSAGE-SCHEMAS.md` 참조.

---

## 4. Redis

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `spring.data.redis.host` | `localhost` | `${REDIS_HOST}` | `REDIS_HOST` |
| `spring.data.redis.port` | `6379` | `${REDIS_PORT:6379}` | `REDIS_PORT` |

---

## 5. Quartz

| Key | dev 기본 | prod 기본 | env var | 비고 |
|---|---|---|---|---|
| `spring.quartz.job-store-type` | `jdbc` | `jdbc` | - | Quartz 테이블(QRTZ_*) 을 `streaming_db` 에 공유 |
| `spring.quartz.jdbc.initialize-schema` | `always` | `never` | - | prod 는 공식 DDL 수동 적용 (`org.quartz/jdbcjobstore/tables_postgres.sql`) |
| `spring.quartz.properties.org.quartz.jobStore.isClustered` | `false` | `false` | - | 단일 노드 가정 (ADR 0002) |
| `spring.quartz.properties.org.quartz.jobStore.driverDelegateClass` | `PostgreSQLDelegate` | 동일 | - | |
| `spring.quartz.properties.org.quartz.threadPool.threadCount` | `5` | `5` | - | |

---

## 6. 서버

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `server.port` | `8088` | `${SERVER_PORT:8088}` | `SERVER_PORT` |
| `server.max-http-header-size` | `64KB` | (미설정, Spring 기본 8KB) | - |

> dev 만 64KB — STOMP CONNECT header 에 긴 sessionToken 이 실려 기본 8KB 를 넘을 수 있기 때문. prod 는 gateway 가 헤더 크기를 먼저 검증하므로 streaming-service 직접 노출 안 되면 영향 적음.

---

## 7. 로깅

| Key | dev 기본 | prod 기본 |
|---|---|---|
| `logging.level.root` | `INFO` | `WARN` |
| `logging.level.com.example` | `DEBUG` | (미설정) |
| `logging.level.com.example.streamingservice` | (미설정) | `INFO` |

---

## 8. streaming-service 전용

### 8.1 JWT (sessionToken)

| Key | dev 기본 | prod 기본 | env var | 허용 범위 |
|---|---|---|---|---|
| `streaming.jwt.secret` | dev placeholder (`${STREAMING_JWT_SECRET:...}`) | `${STREAMING_JWT_SECRET}` (필수) | `STREAMING_JWT_SECRET` | 최소 32 바이트 (HS256) |
| `streaming.jwt.algorithm` | `HS256` | 동일 | - | `HS256` 고정 (ADR 0003) |
| `streaming.jwt.issuer` | `streaming-service` | 동일 | - | |

### 8.2 세션 & 창(window)

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `streaming.session.window-grace-minutes` | `10` | `10` | - |

- `startTime - window-grace-minutes` ~ `endTime + window-grace-minutes` 가 유효 창
- `Schedule.currentStatus` / `canEnterSession` / `canServeHls` 에서 참조

### 8.3 주소(어댑터)

| Key | dev 기본 | prod 기본 | env var | 허용 범위 |
|---|---|---|---|---|
| `streaming.address.adapter` | `${STREAMING_ADDRESS_ADAPTER:local-direct}` | 동일 | `STREAMING_ADDRESS_ADAPTER` | `local-direct` (향후 확장: `signed-url`, `cdn`) |
| `streaming.address.public-base-url` | `${STREAMING_PUBLIC_BASE_URL:http://localhost:8000}` (gateway 경유 기본) | `${STREAMING_PUBLIC_BASE_URL}` (필수) | `STREAMING_PUBLIC_BASE_URL` | `http(s)://host:port` |

- `public-base-url` 은 매니페스트 재작성 시 세그먼트 URL 의 host 부분. dev 는 gateway(8000) 가 기본, 직결 디버깅 시 `http://localhost:8088` 로 override.

### 8.4 채팅

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `streaming.chat.rate-limit-per-second` | `3` | `3` | - |
| `streaming.chat.max-message-length` | `500` | `500` | - |

### 8.5 Viewer 카운트

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `streaming.viewer.broadcast-interval-seconds` | `5` | `5` | - |

### 8.6 스토리지

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `storage.s3-path` | `${STORAGE_S3_PATH:C:/Users/qorwh/s3}` | `${STORAGE_S3_PATH}` (필수) | `STORAGE_S3_PATH` |

- `LocalDirectStreamAddressAdapter` 의 base directory. 반드시 존재하는 경로여야 함.

### 8.7 외부 클라이언트 (creator-service)

| Key | dev 기본 | prod 기본 | env var |
|---|---|---|---|
| `client.creator.base-url` | `${CLIENT_CREATOR_BASE_URL:http://localhost:8080}` | `${CLIENT_CREATOR_BASE_URL}` (필수) | `CLIENT_CREATOR_BASE_URL` |

- `MovieLocationPort` 구현체 `CreatorMovieLocationAdapter` (RestClient) 가 사용.

---

## 9. 전체 prod env var 체크리스트

프로덕션 기동 전에 모두 세팅 필수:

```
DB_HOST
DB_PORT                 # optional, default 5432
DB_NAME                 # optional, default streaming_db
DB_USERNAME
DB_PASSWORD
DDL_AUTO                # optional, default validate
REDIS_HOST
REDIS_PORT              # optional, default 6379
KAFKA_BOOTSTRAP_SERVERS
KAFKA_CONSUMER_GROUP_ID # optional, default streaming-service
SERVER_PORT             # optional, default 8088
STREAMING_JWT_SECRET
STREAMING_ADDRESS_ADAPTER # optional, default local-direct
STREAMING_PUBLIC_BASE_URL
STORAGE_S3_PATH
CLIENT_CREATOR_BASE_URL
```

---

## 10. 관련 문서

- `RUNBOOK.md §3` — 기동 절차
- ADR 0002 (단일 노드) · 0003 (JWT HS256)
- `TOKEN.md` — sessionToken 상세
