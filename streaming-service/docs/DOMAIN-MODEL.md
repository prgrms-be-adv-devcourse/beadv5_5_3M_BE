# Domain Model

`streaming-service` 의 엔티티·값 객체·enum 명세. 실제 JPA 구현(`domain/Schedule.java`, `domain/Entitlement.java`)과 일치하도록 유지한다.

---

## 1. 엔티티 개요

| 엔티티 | 테이블 | 역할 |
|---|---|---|
| `Schedule` | `schedule` | 상영 스케줄 사본. `creator-service` / `ticket-service` 와 동일 PK 재사용. upstream Kafka(`movie.schedule.confirmed`) 에서 적재. |
| `Entitlement` | `entitlement` | 유저 관람 권한. upstream Kafka(`ticket.review.authorized`) 에서 적재. `(userId, scheduleId)` 유니크. |

**소유권 원칙**
- 두 엔티티는 모두 **업스트림이 source of truth**. streaming-service 는 읽기 중심.
- `Schedule.videoPath` 만 예외 — 첫 세션 발급 시 streaming-service 가 `MovieLocationPort` 로 lazy resolve 하여 최초 1회 채운다 (`attachVideoLocation`).
- 그 외 필드 변경 API 없음.

**불변성**
- `Entitlement` 는 완전 불변. 생성만 허용, 수정 메서드 없음.
- `Schedule` 은 상태 전이 없음 (상태는 `currentStatus(now)` 로 시간 기반 계산 — enum 필드 보관 안 함).

---

## 2. `Schedule` 엔티티

### 2.1 테이블: `schedule`

| 필드 | Java 타입 | DB 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|---|
| `scheduleId` | `Long` | `BIGINT` | NOT NULL | PK (identity NOT) | upstream(ticket/creator) 동일 키 재사용 |
| `movieId` | `Long` | `BIGINT` | NOT NULL | | creator-service movie ID |
| `creatorId` | `UUID` | `UUID` | NOT NULL | | |
| `title` | `String` | `VARCHAR(255)` | NOT NULL | | 스케줄 제목 (영상 제목 사본) |
| `startTime` | `Instant` | `TIMESTAMP WITH TIME ZONE` | NOT NULL | | UTC 저장 |
| `endTime` | `Instant` | `TIMESTAMP WITH TIME ZONE` | NOT NULL | | UTC 저장, `> startTime` |
| `videoPath` | `String` | `VARCHAR(1024)` | NULLABLE | | 최초 수신 시 null, lazy resolve 후 업데이트 |
| `runningTime` | `Integer` | `INT` | NULLABLE | ≥0 | 초 단위. `videoPath` 와 함께 채워짐. 정보성. |
| `seats` | `Integer` | `INT` | NOT NULL | ≥0 | viewer count cap 아님, 정보성 |
| `imageUrl` | `String` | `VARCHAR(1024)` | NULLABLE | | 썸네일 |
| `createdAt` | `Instant` | `TIMESTAMP WITH TIME ZONE` | NOT NULL | `@CreationTimestamp` | |
| `updatedAt` | `Instant` | `TIMESTAMP WITH TIME ZONE` | NOT NULL | `@UpdateTimestamp` | |

**인덱스**
- `idx_schedule_startTime (startTime)` — LobbyOpen/StartingSoon Quartz trigger 검색용
- `idx_schedule_endTime (endTime)` — Ended/ForceExit Quartz trigger 검색용

### 2.2 Public 메서드 시그니처 (엔티티 자체 소유 규칙)

```java
/** 현 시각 기준 스케줄 상태 계산. enum 필드로 보관하지 않음. */
public ScheduleStatus currentStatus(Instant now);

/** LOBBY / ON_AIR / POST 중 하나면 true. 세션 발급 가능. */
public boolean canEnterSession(Instant now);

/** ON_AIR 일 때만 true. HLS 세그먼트 서빙 가능. */
public boolean canServeHls(Instant now);

/** now >= endTime + 10m 이면 true. ForceExitJob 이 불변성 assert 용도로 사용. */
public boolean isForceExitTime(Instant now);

/**
 * videoPath + runningTime 을 한번만 세팅. null → non-null 전이만 허용.
 * 재할당 시 ScheduleException.videoPathAlreadySet() 던짐.
 */
public void attachVideoLocation(String videoPath, Integer runningTime);
```

**상태 경계 (정확한 연산자)**

| 상태 | 조건 |
|---|---|
| `CLOSED` | `now < startTime - 10m` |
| `LOBBY` | `startTime - 10m <= now < startTime` |
| `ON_AIR` | `startTime <= now < endTime` |
| `POST` | `endTime <= now < endTime + 10m` |
| `FINISHED` | `now >= endTime + 10m` |

단위: `java.time.Instant`. `startTime - 10m` 은 `startTime.minus(Duration.ofMinutes(10))`.

### 2.3 `ScheduleStatus` enum

```java
public enum ScheduleStatus {
    CLOSED, LOBBY, ON_AIR, POST, FINISHED
}
```

---

## 3. `Entitlement` 엔티티

### 3.1 테이블: `entitlement`

| 필드 | Java 타입 | DB 타입 | NULL | 제약 |
|---|---|---|---|---|
| `id` | `Long` | `BIGINT` | NOT NULL | PK (identity) |
| `userId` | `UUID` | `UUID` | NOT NULL | |
| `scheduleId` | `Long` | `BIGINT` | NOT NULL | FK → `schedule.scheduleId` |
| `ticketId` | `Long` | `BIGINT` | NOT NULL | upstream ticket-service ID |
| `authorizedAt` | `Instant` | `TIMESTAMP WITH TIME ZONE` | NOT NULL | |

**유니크**
- `uk_entitlement_user_schedule (userId, scheduleId)` — `(userId, scheduleId)` 로 중복 소비 방어

**인덱스**
- `idx_entitlement_scheduleId (scheduleId)` — ForceExit / 관리 조회용

### 3.2 Public 메서드

없음. Record-style 불변. 생성자·getter 만. 상태 변경 없음.

---

## 4. 값 객체 / Enum

### 4.1 `SessionKickReason`

```java
public enum SessionKickReason {
    DUPLICATE_LOGIN,   // 같은 유저가 다른 기기에서 새 세션 발급 → 기존 세션 kick
    FORCE_EXIT,        // ForceExitJob 발동
    ADMIN_ACTION       // (future) 관리자 강제 해제
}
```

용도: `KickNotifierPort.notify(userId, reason)` + `/user/queue/kick` 페이로드 `reason` 필드.

### 4.2 `StreamState`

```java
public enum StreamState {
    LOBBY_OPEN,        // LobbyOpenJob
    STARTING_SOON,     // StartingSoonJob (startTime - 1m 등)
    STARTED,           // StartedJob (startTime)
    ENDING_SOON,       // EndingSoonJob (endTime - 1m 등)
    ENDED,             // EndedJob (endTime)
    FORCE_EXIT         // ForceExitJob (endTime + 10m)
}
```

용도: `/topic/state/schedule/{id}` payload `state` 필드.

### 4.3 `ActiveSession` (record)

```java
public record ActiveSession(UUID sessionId, long scheduleId, Instant issuedAt) {}
```

Redis `stream:session:user:{userId}` 에 serialize. `SessionCachePort.findByUser` 반환형.

### 4.4 `SessionMeta` (record)

```java
public record SessionMeta(UUID userId, long scheduleId) {}
```

Redis `stream:session:id:{sessionId}` 에 serialize. `SessionCachePort.findBySession` 반환형.

### 4.5 `MovieLocation` (record)

```java
public record MovieLocation(
    long movieId,
    String videoUrl,         // creator-service DB 저장값 (상대경로)
    Integer runningTime,     // 초
    String title,
    UUID creatorId
) {}
```

`MovieLocationPort.fetch(movieId)` 반환형. `EnterStreamService` 가 `schedule.attachVideoLocation(videoUrl, runningTime)` 에 사용.

### 4.6 `HlsResource` (record)

```java
public record HlsResource(
    InputStream body,
    String contentType,   // "application/vnd.apple.mpegurl" 또는 "video/mp2t"
    long contentLength
) {}
```

`StreamAddressPort.openSegment` 반환형. 서비스 층이 InputStream 을 Controller 의 `StreamingResponseBody` 에 연결.

### 4.7 채팅 닉네임 형식 (익명 채팅)

채팅 payload `nickname` 필드는 `"관람객#" + ticketId` 형식으로 **서버가 계산**한다. DB 에 별도 저장하지 않고, WebSocket STOMP CONNECT 처리 중 `StompAuthChannelInterceptor` 가 `Entitlement.ticketId` 로 한 번 계산해 `simpSessionAttributes.nickname` 에 캐시한다. 클라이언트가 보낸 nickname 은 무시된다. 실명·이메일·user-service HTTP 조회 없음 — 익명 채팅 정책으로 외부 노출을 차단. 예: `"관람객#1001"`, `"관람객#42"`. 동일 유저·동일 스케줄 재접속 시 동일 닉네임 유지 (`Entitlement` unique constraint 보장).

---

## 5. DDL Sketch

### 5.1 PostgreSQL (`dev`/`prod`)

```sql
CREATE TABLE schedule (
    schedule_id BIGINT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    creator_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    video_path VARCHAR(1024),
    running_time INT,
    seats INT NOT NULL,
    image_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_schedule_start_time ON schedule(start_time);
CREATE INDEX idx_schedule_end_time ON schedule(end_time);

CREATE TABLE entitlement (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    schedule_id BIGINT NOT NULL REFERENCES schedule(schedule_id),
    ticket_id BIGINT NOT NULL,
    authorized_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_entitlement_user_schedule UNIQUE (user_id, schedule_id)
);
CREATE INDEX idx_entitlement_schedule_id ON entitlement(schedule_id);
```

JPA ddl-auto=create 로 자동 생성되지만, 마이그레이션 도구 도입 시 위 DDL 을 초기 스키마로 사용.

### 5.2 H2 (test)

H2 에서 `UUID` 는 표준 지원, `TIMESTAMP WITH TIME ZONE` 은 `TIMESTAMP WITH TIME ZONE` 또는 `TIMESTAMP` 로 대체. `BIGSERIAL` 은 `BIGINT AUTO_INCREMENT`.

```sql
CREATE TABLE schedule (
    schedule_id BIGINT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    creator_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    video_path VARCHAR(1024),
    running_time INT,
    seats INT NOT NULL,
    image_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE entitlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id UUID NOT NULL,
    schedule_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    authorized_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_entitlement_user_schedule UNIQUE (user_id, schedule_id),
    FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
);
```

---

## 6. 테스트용 시드 쿼리

수동 e2e 시나리오용 최소 시드. `schedule_id=42`, 고정 `creator_id`, `user_id` 2개 상정. `e2e/publish_events.py` 와 함께 사용.

```sql
-- (S1) LOBBY 에 있는 스케줄 (now 기준 startTime 이 5분 후)
INSERT INTO schedule (schedule_id, movie_id, creator_id, title, start_time, end_time,
                     video_path, running_time, seats, image_url, created_at, updated_at)
VALUES (42, 100, '00000000-0000-0000-0000-000000000001', 'Test Live',
        NOW() + INTERVAL '5 minutes', NOW() + INTERVAL '2 hours 5 minutes',
        NULL, NULL, 500, NULL, NOW(), NOW());

-- (S2) user A 권한
INSERT INTO entitlement (user_id, schedule_id, ticket_id, authorized_at)
VALUES ('11111111-1111-1111-1111-111111111111', 42, 1001, NOW());

-- (S3) user B 권한 (단일 세션 enforcement 테스트용)
INSERT INTO entitlement (user_id, schedule_id, ticket_id, authorized_at)
VALUES ('22222222-2222-2222-2222-222222222222', 42, 1002, NOW());
```

---

## 7. 관련 문서

- `MESSAGE-SCHEMAS.md` — Kafka payload → 엔티티 매핑
- `DESIGN.md §4` — 레이어 구조 · Redis 키 · 포트/어댑터
- `DESIGN.md §5.3` — `EnterStreamService` 플로우 (`attachVideoLocation` 호출 지점)
- `adr/0002-single-node-deployment.md` — 단일 노드 가정
