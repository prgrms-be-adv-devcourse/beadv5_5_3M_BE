# 트러블슈팅: HikariCP 커넥션 풀 고갈 (QueueService 핫패스 DB 조회)

## 장애 개요

| 항목 | 내용 |
|------|------|
| 발생 일시 | 2026-04-17 12:05:43 KST |
| 영향 범위 | 대기열 진입 (`POST /api/queue/{scheduleId}/enter`) 전체 |
| 증상 | 요청이 30초 대기 후 HTTP 500 에러 반환 |
| 피크 대기 스레드 | 81개 (HikariCP waiting) |

---

## 에러 메시지

```
java.sql.SQLTransientConnectionException:
  HikariPool-1 - Connection is not available, request timed out after 30001ms
```

HikariCP 상태:
```
total=10, active=10, idle=0, waiting=81
```

---

## 근본 원인 분석

### 문제 코드 위치

`QueueService.enter()` — `com.example.ticketservice.application.service.QueueService`

```java
// Before: DB 조회가 핫패스에 존재
public QueueEntryResponse enter(UUID userId, Long scheduleId) {
    Schedule schedule = scheduleRepository.findById(scheduleId)  // ← 병목
            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

    if (schedule.getStatus() != ScheduleStatus.TICKETING) {
        throw QueueErrorCode.QUEUE_NOT_OPEN.of(scheduleId);
    }

    int ticketNum = (int) (schedule.getSeats() - stockAfterDecr);   // seats 사용
    ...
    throw TicketErrorCode.INSUFFICIENT_BALANCE.of((long) schedule.getCookie()); // cookie 사용
    ...
    Duration ttl = Duration.between(LocalDateTime.now(),
        schedule.getStartTime().minusMinutes(10));                   // startTime 사용
}
```

### 왜 이 조회가 병목이 되었나

1. `QueueService.enter()`는 `@Transactional` 어노테이션이 없지만,
   `scheduleRepository.findById()`는 내부적으로 HikariCP에서 커넥션을 획득한다.
2. 티켓팅 시작 순간 수백 개의 동시 요청이 몰리면서 커넥션 풀(max=10)이 즉시 포화됐다.
3. `scheduleRepository.findById()`는 단순 PK 조회임에도 불구하고, 커넥션 획득 자체가
   대기 중인 81개 스레드 뒤에 줄을 서야 했다.
4. 30초(기본 `connectionTimeout`) 후 `SQLTransientConnectionException` 발생.

### 조회 목적 — 딱 3가지

| 사용처 | 값 | 변경 가능성 |
|--------|-----|-----------|
| TICKETING 상태 확인 | `schedule.status` | 티켓팅 시작 후 불변 |
| ticketNum 계산 | `schedule.seats` | 불변 |
| 대기열 TTL 계산 | `schedule.startTime` | 불변 |
| 에러 메시지 | `schedule.cookie` | 불변 |

세 값 모두 **티켓팅 시작(`TicketingStartService.execute()`) 이후 변경되지 않는다.**

---

## 해결 방법

### 단기 조치 — 커넥션 풀 확대

`src/main/resources/application-prod.yaml`

```yaml
# Before
hikari:
  maximum-pool-size: 10

# After
hikari:
  maximum-pool-size: 25
```

Tomcat 기본 스레드 200 기준으로 20~30이 적정 수준.
50 이상은 PostgreSQL 부담 및 메모리 비용 대비 효과가 낮다.

> **한계:** 트래픽이 충분히 크면 25도 고갈된다. 근본 해결이 필요하다.

---

### 근본 해결 — 핫패스 DB 조회 제거

#### 핵심 아이디어

`TicketingStartService`가 이미 `stock:schedule:{id}`와 `paying:schedule:{id}`를
Redis에 쓰는 시점에 `seats`, `cookie`, `startTime`도 함께 캐싱한다.

- `stock:schedule:{id}` 키 존재 = TICKETING 상태 → 별도 DB 조회 불필요
- 나머지 3개 값도 Redis에서 O(1)로 조회

#### 변경 1: `TicketingStartService.execute()` — 3개 키 추가 캐싱

```java
// 기존 (stock, paying만 설정)
cachePort.setCounter(STOCK_KEY_PREFIX  + scheduleId, remaining, ttl);
cachePort.setCounter(PAYING_KEY_PREFIX + scheduleId, 0, ttl);

// 추가
cachePort.setCounter(SEATS_KEY_PREFIX      + scheduleId, schedule.getSeats(),   ttl);
cachePort.setCounter(COOKIE_KEY_PREFIX     + scheduleId, schedule.getCookie(),  ttl);
cachePort.set(START_TIME_KEY_PREFIX        + scheduleId, schedule.getStartTime().toString(), ttl);
```

TTL은 `stock` / `paying` 과 동일(`startTime - 10min`) → 항상 함께 존재하거나 함께 만료.

#### 변경 2: `QueueService.enter()` — DB 의존성 완전 제거

```java
// After: DB 조회 없음
public QueueEntryResponse enter(UUID userId, Long scheduleId) {
    String stockKey = STOCK_KEY_PREFIX + scheduleId;
    if (!cachePort.exists(stockKey)) {              // stock 키 없음 = 티켓팅 아님
        throw QueueErrorCode.QUEUE_NOT_OPEN.of(scheduleId);
    }

    Long stockAfterDecr = cachePort.decrement(stockKey);

    if (stockAfterDecr != null && stockAfterDecr >= 0) {
        Long seats = cachePort.getCounter(SEATS_KEY_PREFIX + scheduleId);   // Redis
        int ticketNum = (int) (seats - stockAfterDecr);
        ...
        Long cookie = cachePort.getCounter(COOKIE_KEY_PREFIX + scheduleId); // Redis
        throw TicketErrorCode.INSUFFICIENT_BALANCE.of(cookie != null ? cookie : 0L);
    }

    // 대기열 진입 시 TTL 계산
    String startTimeStr = cachePort.get(START_TIME_KEY_PREFIX + scheduleId, String.class)
        .orElse(null);
    LocalDateTime startTime = startTimeStr != null
        ? LocalDateTime.parse(startTimeStr) : LocalDateTime.now();
    Duration ttl = Duration.between(LocalDateTime.now(), startTime.minusMinutes(10));
    ...
}
```

`scheduleRepository` 필드 및 관련 import 4개 완전 제거.

---

## 개선 효과

| 항목 | Before | After |
|------|--------|-------|
| enter() DB 쿼리 | 요청당 1회 (PK 조회) | **0회** |
| HikariCP 점유 (enter 경로) | 요청당 1 커넥션 | **0 커넥션** |
| 응답 지연 (DB 조회) | ~5~20ms | **~0ms** (Redis ~0.1ms) |
| 500 동시 요청 처리 시간 | 30.82초 (타임아웃) | **0.56초** |
| E2E 500명 동시 진입 결과 | 500 에러 다수 | **0 에러, PURCHASED 정확히 50개** |

---

## 새로 추가된 Redis 키

| 키 | 타입 | 값 | 설정 위치 |
|----|------|----|----------|
| `seats:schedule:{id}` | Counter | 총 좌석 수 | `TicketingStartService` |
| `cookie:schedule:{id}` | Counter | 티켓 가격(쿠키) | `TicketingStartService` |
| `startTime:schedule:{id}` | String (ISO-8601) | 공연 시작 시각 | `TicketingStartService` |

TTL: `startTime - 10min - now` (stock/paying 키와 동일)

---

## 재발 방지

1. **핫패스 DB 조회 금지 원칙**: 초당 수백 요청 이상이 예상되는 엔드포인트는
   DB 조회를 두지 않는다. 필요한 불변 값은 초기화 시점에 Redis에 캐싱한다.

2. **커넥션 풀 사이징**: `maximum-pool-size`는 Tomcat 스레드 수의 10~15% 수준을 기준으로
   설정한다 (Tomcat 200 스레드 → pool 20~30). DB 쿼리가 핫패스에서 제거된 현재는
   25로도 충분하다.

3. **관련 문서**:
   - Redis 키 명세 → [`04-redis-keys-and-ttl.md`](./04-redis-keys-and-ttl.md)
   - 대기열 흐름 → [`03-queue-flow-and-implementation.md`](./03-queue-flow-and-implementation.md)
