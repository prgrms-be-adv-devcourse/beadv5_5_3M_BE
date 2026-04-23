# Troubleshooting

운영·개발 중 자주 만나는 문제와 해결 순서. 각 항목: **증상 → 원인 → 확인 → 해결**.

로컬 구동 절차 자체는 [RUNBOOK.md](RUNBOOK.md) 참고.

---

## 1. 세션 발급 실패 (`POST /api/streaming/sessions`)

### 1.1 403 `NO_ENTITLEMENT`

- **원인**: 해당 `scheduleId` 에 대한 entitlement row 가 없음.
- **확인**:
  ```bash
  psql -h localhost -U user -d streaming_db -c \
    "SELECT * FROM entitlement WHERE user_id='<UUID>' AND schedule_id=<id>;"
  ```
- **해결**:
  1. ticket-service 가 `ticket.review.authorized` 를 실제로 발행했는지 — ticket-service 로그의 `ReviewAuthQuartzJob` 발화 메시지 확인.
  2. Kafka consumer(`TicketReviewAuthorizedListener`) 가 해당 메시지를 받았는지 — streaming-service 로그에 "DataIntegrityViolationException" 혹은 DLT 기록 확인.
  3. 테스트 환경이면 `e2e/publish_events.py` 로 직접 entitlement 이벤트 주입.

### 1.2 404 `SCHEDULE_NOT_FOUND`

- **원인**: `schedule` row 가 없음 — `movie.schedule.confirmed` 를 받지 못했거나 처리 실패.
- **확인**:
  ```bash
  psql -d streaming_db -c "SELECT schedule_id, title FROM schedule;"
  ```
- **해결**:
  - `ScheduleConfirmedListener` 가 DLT 로 빠졌는지 (`-dlt` 토픽 consume)
  - creator-service 의 `movie.schedule.confirmed` producer 로그 확인 — `scheduleId` 가 같은지

### 1.3 410 `WINDOW_CLOSED`

- **원인**: 현재 시각이 `[startTime − 10m, endTime + 10m)` 밖.
- **확인**:
  ```sql
  SELECT schedule_id,
         start_time,
         end_time,
         NOW() AS now,
         (start_time - INTERVAL '10 minutes') AS lobby_open,
         (end_time + INTERVAL '10 minutes') AS force_exit
  FROM schedule;
  ```
- **해결**:
  - 로컬 개발 시 `start_time` 을 현재 시각 기준으로 앞당기기:
    ```sql
    UPDATE schedule
    SET start_time = NOW() + INTERVAL '1 minute',
        end_time   = NOW() + INTERVAL '1 hour 1 minute'
    WHERE schedule_id = 42;
    ```
  - Quartz Job trigger 를 재생성하려면 Kafka 재발행 (`publish_events.py`) 또는 `JobTestController` 로 강제 재등록.

### 1.4 502 `STREAM_LOCATION_UNAVAILABLE`

- **원인**: `CreatorMovieLocationAdapter` 가 creator-service `/internal/movies/{id}/location` 호출에 실패.
- **확인**:
  ```bash
  curl -v http://localhost:8080/internal/movies/<movieId>/location
  ```
- **해결**:
  - creator-service 가 구동 중인지 (`curl http://localhost:8080/actuator/health`)
  - `client.creator.base-url` property 가 올바른지 (CONFIG.md §8.7)
  - 해당 movie 가 HLS 산출물을 보유한지 — creator-service 의 업로드·인코딩 완료 여부

---

## 2. WebSocket CONNECT 실패

### 2.1 `token` 헤더 없음

- **증상**: CONNECT 직후 ERROR 프레임 + close.
- **원인**: STOMP CONNECT 프레임의 `token` native header 누락.
- **확인**: 브라우저 DevTools → Network → WS → Frames 탭에서 CONNECT 프레임 헤더 확인.
- **해결**: 클라이언트 측 `connectHeaders: { token: sessionToken }` 설정. URL 쿼리(`?t=...`) 가 아님.

### 2.2 `INVALID_TOKEN` / `SESSION_EXPIRED`

- **원인**:
  - `INVALID_TOKEN` — JWT 서명 검증 실패. `streaming.jwt.secret` 이 발급 시점과 다름 (서버 재시작 시 secret 교체된 경우).
  - `SESSION_EXPIRED` — JWT `exp` 지났거나 Redis `stream:session:id:{sessionId}` 키가 지워짐 (kick / ForceExit / TTL 만료).
- **확인**:
  ```bash
  # Redis 에 세션 키 존재 여부
  redis-cli KEYS "stream:session:id:*"
  # JWT 내용 확인 (페이로드만 base64 디코딩)
  echo "<jwt>" | cut -d. -f2 | base64 -d
  ```
- **해결**: 새 세션 발급 (`POST /api/streaming/sessions` 재호출).

### 2.3 CONNECT 는 되는데 SUBSCRIBE 에서 `SESSION_MISMATCH`

- **원인**: `/topic/chat/schedule/99` 를 구독하는데 세션 attribute 의 `scheduleId` 는 42.
- **확인**: 발급받은 session 의 scheduleId 와 구독 destination 의 scheduleId 일치 여부.
- **해결**: 구독 destination 의 scheduleId 를 세션 발급 시 사용한 scheduleId 와 일치시키기. 다른 스케줄을 보려면 세션을 새로 발급해야 함.

---

## 3. HLS 매니페스트/세그먼트 404·400

### 3.1 매니페스트 요청 → 404

- **원인**:
  - `schedule.video_path` null — `MovieLocationPort.fetch` 가 호출 안 됐거나 실패
  - 파일이 `storage.s3-path` 아래 실제로 없음
- **확인**:
  ```sql
  SELECT schedule_id, video_path FROM schedule WHERE schedule_id=<id>;
  ```
  ```bash
  ls "$STORAGE_S3_PATH/<video_path>/index.m3u8"
  ```
- **해결**:
  - `video_path` 가 null 이면 → 세션 발급 (`POST /api/streaming/sessions`) 해서 lazy 조회 트리거
  - 파일 실제로 없으면 → creator-service 의 HLS 인코딩 파이프라인 확인

### 3.2 세그먼트 요청 → 400 `INVALID_FILE_NAME`

- **원인**: 파일명이 정규식 `^[A-Za-z0-9_-]+\.(m3u8|ts)$` 에 매치 안 됨, 또는 path traversal 시도 (`..`).
- **확인**: 요청 URL 의 `{file}` 부분 확인.
- **해결**: 클라이언트 플레이어가 생성한 URL 이면 매니페스트 rewrite 규칙 확인 — `ManifestRewriter` 가 세그먼트 파일명을 원본 그대로 유지하는지.

### 3.3 `WINDOW_CLOSED` (410)

- **원인**: HLS 는 ON_AIR 구간(`[startTime, endTime]`) 에서만 서빙. 대기실/마무리 요청은 거부.
- **해결**: 시간 맞을 때까지 대기 또는 테스트용 schedule time 수정 (§1.3 SQL).

---

## 4. Kafka 관련

### 4.1 재시작 후 같은 메시지 재처리

- **증상**: 서버 재시작 시 `DataIntegrityViolationException (entitlement duplicate)` 로그 반복.
- **원인**: at-least-once 동작 — ack 되지 않은 메시지가 재전달. 정상 동작.
- **확인**: `TicketReviewAuthorizedListener` 가 선조회 + exception catch 로 조용히 무시하는지.
- **해결**: 로그가 DEBUG 레벨이면 정상. ERROR 레벨이면 listener 코드 검토.

### 4.2 DLT 로 메시지가 빠짐

- **증상**: 원 토픽에서 소비 안 되고 `<토픽>-dlt` 에 쌓임.
- **원인**: 3회 재시도 모두 실패 (backoff 1s → 2s → 4s).
- **확인**:
  ```bash
  kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic movie.schedule.confirmed-dlt --from-beginning
  ```
- **해결**: 실패 원인(payload 포맷·DB 제약·외부 서비스 응답) 확인 후 수정 → 수동으로 원 토픽에 재발행.

### 4.3 Consumer group offset reset

- **증상**: 모든 Kafka 메시지가 처리 안 됨. 로그에 "no messages" 계속.
- **확인**:
  ```bash
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --describe --group streaming-service-dev
  ```
- **해결**:
  ```bash
  # 처음부터 재소비 (개발 시)
  kafka-consumer-groups --bootstrap-server localhost:9092 \
    --group streaming-service-dev --reset-offsets --to-earliest \
    --topic movie.schedule.confirmed --execute
  ```

---

## 5. Quartz 관련

### 5.1 Job 이 발동 안 함

- **증상**: 예상 시각이 지났는데 `/topic/state/*` 푸시가 없음.
- **확인**:
  ```sql
  SELECT trigger_name, next_fire_time, trigger_state
  FROM qrtz_triggers
  WHERE job_name LIKE 'schedule-<id>-%';
  ```
- **해결**:
  - `next_fire_time` 이 과거 → MISFIRE. Quartz 기본 misfire policy 적용됨 (`smart_policy`). 재 ingest 로 새 trigger 생성.
  - row 자체 없음 → `SchedulerPort.scheduleLifecycle` 미호출. Kafka `movie.schedule.confirmed` 재발행 또는 `JobTestController` 로 강제 등록.

### 5.2 `isClustered=false` 인데 두 인스턴스 띄움

- **증상**: 같은 Job 이 두 번 발화 또는 충돌 로그.
- **원인**: 단일 노드 전제 위반 (ADR 0002).
- **해결**: 인스턴스 1개로 축소. 다중 인스턴스 필요 시 `isClustered=true` + Quartz `QRTZ_LOCKS` 가 락 역할, 동시 `fetchAndLockNextTriggers` 방지. 하지만 WebSocket 브로커는 여전히 SimpleBroker (노드 간 broadcast 불가) — 대대적 재설계 필요.

### 5.3 Quartz 스키마 충돌

- **증상**: `ERROR: relation "qrtz_locks" does not exist`.
- **확인**: `spring.quartz.jdbc.initialize-schema` 설정.
- **해결**:
  - dev: `always` 로 자동 생성 (yaml 기본).
  - prod: Quartz 공식 SQL 스크립트 수동 실행:
    ```bash
    psql -d streaming_db -f quartz-tables_postgres.sql
    ```

---

## 6. ForceExit 이후 재접속 가능한가?

- **증상**: `endTime + 10m` 지난 schedule 에 대해 클라이언트가 재연결 시도.
- **기대 동작**:
  - `POST /api/streaming/sessions` → `WINDOW_CLOSED` (410)
  - 기존 WebSocket 은 이미 close 됨 (`/user/{userId}/queue/kick` + server close)
  - HLS 요청도 `WINDOW_CLOSED`
- **클라이언트 처리**: `FORCE_EXIT` state 메시지 수신 시 UI 안내 후 재접속 시도 중단.

---

## 7. 채팅 rate limit / 메시지 길이

### 7.1 `CHAT_RATE_LIMITED`

- **원인**: 유저당 3 msg/sec 초과 (Redis 슬라이딩 카운터 TTL 1초).
- **확인**:
  ```bash
  redis-cli GET stream:ratelimit:chat:<userId>
  ```
- **해결**:
  - 기본값 완화: `streaming.chat.rate-limit-per-second` 증가 (CONFIG.md §8.4). 단일 노드 기준만 정확.
  - 자동 테스트가 빠르게 여러 메시지 쏘는 경우 — 테스트 쪽에 `Thread.sleep(400)` 등 간격 추가.

### 7.2 `CHAT_MESSAGE_TOO_LONG`

- **원인**: content > 500자.
- **해결**: `streaming.chat.max-message-length` 조정 또는 클라이언트 단에서 자르기.

---

## 8. Redis 관련

### 8.1 세션 키가 TTL 전에 사라짐

- **원인**:
  - 새 세션 발급으로 기존 `session:id:{oldSessionId}` 가 명시적으로 DEL (단일 세션 enforcement)
  - `ForceExitJob` 이 SCAN + DEL
  - Redis 자체가 재시작됨 (AOF/RDB 없으면 휘발)
- **확인**:
  ```bash
  redis-cli TTL stream:session:id:<sessionId>
  ```
- **해결**:
  - 재발급
  - 운영 환경이면 Redis 영속화 (AOF) 활성화 검토

### 8.2 viewer count 가 안 맞음

- **증상**: `/topic/viewers/schedule/{id}` 에서 받은 count 가 실제 연결 수와 다름.
- **원인**: DISCONNECT 이벤트 누락 (비정상 종료), 또는 다른 인스턴스가 해당 Redis 를 공유하는데 SimpleBroker 는 노드 간 broadcast 없음.
- **확인**:
  ```bash
  redis-cli SMEMBERS stream:viewers:schedule:<id>
  redis-cli SCARD stream:viewers:schedule:<id>
  ```
- **해결**:
  - 5초 주기 `@Scheduled` broadcast 가 동기화 (`ViewerCountService`)
  - 심하면 `SREM` 누락된 userId 수동 제거
  - 근본: 다중 인스턴스 환경이면 아키텍처 재검토

---

## 9. 개발 환경 특이사항

### 9.1 Windows 경로 구분자

- `storage.s3-path: C:/Users/qorwh/s3` 는 forward slash 사용. backslash 쓰면 yaml escape 필요.
- path traversal 검사가 `Path.normalize().startsWith(baseDir)` 로 수행 — Windows/Linux 모두 동작.

### 9.2 `application-dev.yaml` 개인 설정

- `.gitignore` 대상. 팀원 간 공유되지 않음.
- 최초 구동 시 팀 Notion·Wiki 등에서 템플릿 복사해 개인 환경에 맞게 수정.

### 9.3 다른 개발자의 브로커와 충돌

- 공유 Kafka 쓰면 `group-id` 충돌 주의. `streaming-service-<your-name>` 같이 개별화.

---

## 10. 일반 원칙

- **로그 먼저**: streaming-service 자체 로그 (`logs/` 또는 콘솔) 에서 stack trace 확인.
- **DB 상태**: `schedule`, `entitlement`, `QRTZ_TRIGGERS` 세 테이블이 primary ground truth.
- **Redis 상태**: `stream:session:*`, `stream:viewers:*`, `stream:ratelimit:chat:*` 네 패턴.
- **시간 문제 의심 시**: `SELECT NOW()` vs `schedule.start_time` 대조. KST 가정 (`ZoneId.of("Asia/Seoul")`).
- **외부 서비스 먼저 확인**: `/actuator/health` 로 creator/user/ticket/gateway 상태 먼저 체크.

---

## 11. 관련 문서

- [RUNBOOK.md §5](RUNBOOK.md) — 로컬 구동 중 트러블슈팅 (기초)
- [API.md §4](API.md) — 에러 카탈로그 전체
- [DESIGN.md §6](DESIGN.md) — 에러 모델
- [CONFIG.md](CONFIG.md) — 튜닝할 수 있는 값들
