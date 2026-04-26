# Stage 3 — 출력 포트 & 어댑터

> **목표**: Hexagonal의 핵심을 손으로 확인한다. 애플리케이션이 **포트(인터페이스)만 의존**하고 구현 세부를 모른다는 게 무슨 말인지 코드로 본다.
> **예상 소요**: 1일

---

## 0. 포트 ↔ 어댑터 매핑

| 포트 (application/port/out) | 어댑터 (infrastructure) | 메시지 인프라 |
|------------------------------|--------------------------|----------------|
| `CachePort` | `RedisCacheAdapter` | Redis (String/Set/ZSet) |
| `EventPublisherPort` (단건·Primary) | `KafkaEventPublisher extends AbstractKafkaPublisher` | Kafka |
| `EventPublisherPort` (대량·보조) | `KafkaBulkEventPublisher extends AbstractKafkaPublisher` | Kafka (snappy 압축) |
| `UserPort` | `UserClient` | HTTP (RestClient) |
| `SchedulerPort` | `QuartzSchedulerAdapter` | Quartz |
| `TicketProvideBatchPort` | `TicketProvideBatchAdapter` | Spring Batch |
| `TicketCleanupBatchPort` | `TicketCleanupBatchAdapter` | Spring Batch |

**포트 이름에 "Redis" 대신 "Cache"가 있는 이유**: 도메인이 "캐싱이 필요하다"만 알고, "Redis를 쓴다"는 모르게 하려는 것. 다만 `popMinFromZSet` 같은 ZSet-특화 메서드가 있어 현실적으로는 Redis에 강결합.

---

## 1. Redis 기본기 리마인더 (필요 시)

| 연산 | 원자성 | 이 서비스에서 쓰임 |
|------|--------|---------------------|
| `INCR/DECR` | ✅ 단일 키에 대해 원자 | `stock`, `paying`, `cart:count` |
| `SET k v EX <ttl>` | ✅ | `seats`, `cookie`, `startTime` (티켓팅 시작 시 한 번) |
| `MGET k1 k2 ...` | ✅ 다건 키 일괄 조회 (Redis 라운드트립 1회) | `getCounters` — `ScheduleQueryService`에서 TICKETING 회차 stock 일괄 |
| `DEL k` | ✅ 반환값으로 "이전 존재 여부" 제공 | `queue` 종료 (중복 발행 방지) |
| `ZADD k score member` | ✅ 단일 member | 큐 입장 |
| `ZPOPMIN k` | ✅ 가장 낮은 점수 원소 원자 제거 | 대기열 드레인 |
| **여러 커맨드 조합** | ❌ 원자성 없음 | → **DECR-first 패턴으로 회피** |

---

## 2. CachePort — 도메인이 보는 캐시 추상

`application/port/out/CachePort.java` 핵심 발췌:

```java
public interface CachePort {
    // 일반 값
    void set(String key, Object value, Duration ttl);
    <T> Optional<T> get(String key, Class<T> type);
    boolean delete(String key);
    boolean exists(String key);
    void expireKey(String key, Duration ttl);

    // Counter (Redis String 정수 인코딩)
    void setCounter(String key, long value, Duration ttl);
    Long increment(String key);
    Long decrement(String key);
    Long getCounter(String key);
    Map<String, Long> getCounters(Collection<String> keys);  // ← Redis MGET, 다건 일괄 조회 (null 키는 결과 Map에서 제외)

    // ZSet
    void addToZSetWithTimestamp(String key, String member);
    String popMinFromZSet(String key);
    Long getZSetRank(String key, String member);
    Long getZSetSize(String key);
}
```

### 주목할 점

- `setCounter(key, value, ttl)` — 초기값 + TTL을 한 번에. 티켓팅 시작 시 `stock/paying/cart:count` 초기화에 사용.
- `delete(key)` — **boolean 반환**. "이전에 존재했으면 true, 없었으면 false". `QueueAutoProcessService.terminateQueue`에서 중복 발행 방지에 활용.
- `popMinFromZSet` 반환 String — `null`이면 "큐가 비었음".
- `expireKey` 단독 TTL 설정 — 이 서비스에서는 거의 안 씀 (세팅과 동시에 TTL 주는 편).

---

## 3. RedisCacheAdapter — 실제 구현

`infrastructure/caching/adapter/RedisCacheAdapter.java` 주요 발췌:

```java
@Override
public Long increment(String key) {
    Long result = redisTemplate.opsForValue().increment(key);
    log.debug("Redis INCR - key: {}, result: {}", key, result);
    return result;
}

@Override
public Long decrement(String key) {
    Long result = redisTemplate.opsForValue().decrement(key);
    log.debug("Redis DECR - key: {}, result: {}", key, result);
    return result;
}

@Override
public boolean delete(String key) {
    Boolean deleted = redisTemplate.delete(key);
    return Boolean.TRUE.equals(deleted);
}

@Override
public String popMinFromZSet(String key) {
    ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet().popMin(key);
    return tuple != null ? tuple.getValue() : null;
}

@Override
public void addToZSetWithTimestamp(String key, String member) {
    redisTemplate.opsForZSet().add(key, member, System.currentTimeMillis());
}

@Override
public Map<String, Long> getCounters(Collection<String> keys) {
    if (keys == null || keys.isEmpty()) return Map.of();
    List<String> ordered = new ArrayList<>(keys);
    List<String> values = redisTemplate.opsForValue().multiGet(ordered);  // ← Redis MGET
    Map<String, Long> result = new HashMap<>(ordered.size());
    if (values == null) return result;
    for (int i = 0; i < ordered.size(); i++) {
        String v = i < values.size() ? values.get(i) : null;
        if (v != null) result.put(ordered.get(i), Long.parseLong(v));
    }
    return result;
}
```

**주목할 점**:
- `StringRedisTemplate`만 사용 — 모든 값은 String 직렬화.
- 객체 저장 시 `KafkaMessageUtil.serialize(...)`로 JSON 직렬화. 캐시에 JSON 저장.
- `addToZSetWithTimestamp`: **점수 = 현재 시각 millis**. FIFO 대기열이 되는 이유.
- `getCounters`: `multiGet`은 라운드트립 1회. **null인 키는 결과 Map에 넣지 않음** — 호출 측에서 `map.get(key) == null`로 "TTL 만료/미시딩"을 자연스럽게 분기.

---

## 4. Redis 키 7종 — 언제·어디서 건드려지나

`application/constants/RedisKeys.java`:

```java
public static final String STOCK      = "stock:schedule:";
public static final String QUEUE      = "queue:schedule:";
public static final String PAYING     = "paying:schedule:";
public static final String SEATS      = "seats:schedule:";
public static final String COOKIE     = "cookie:schedule:";
public static final String START_TIME = "startTime:schedule:";
public static final String CART_COUNT = "cart:count:schedule:";
```

### 키별 LifeCycle 표

| 키 | 생성 시점 | 읽기 시점 | 쓰기 시점 | 삭제/만료 |
|----|-----------|-----------|-----------|-----------|
| `cart:count` | 첫 addToCart | `getCartCount()` | `CartUpdatedEvent` AFTER_COMMIT | CartClose 시 (TTL 없음 → 수동 DEL) |
| `stock` | `handleTicketingStarted` | `drainQueue`, SelfPayment 실패 분기 | DECR: collectWindow / INCR: 실패·환불 | TTL = startTime-10min |
| `queue` | `QueueService.enter` (ZADD) | `drainQueue` sized, `getZSetRank` | ZPOPMIN: collectWindow / ZADD: 유저 입장 | DEL on terminate / TTL |
| `paying` | `handleTicketingStarted` (초기 0) | `checkTermination` | INCR: collectWindow / DECR: finally | TTL = startTime-10min |
| `seats` | `handleTicketingStarted` | `computeTicketNum` | 한 번만 (변경 없음) | TTL |
| `cookie` | `handleTicketingStarted` | 이 서비스에선 거의 안 씀 (Schedule에서 직접 읽음) | 한 번만 | TTL |
| `startTime` | `handleTicketingStarted` | 큐 입장 TTL 계산 | 한 번만 | TTL |

**왜 TTL이 `startTime-10min`?** → 스트리밍 시작 10분 전까지는 환불·조회가 가능 → 그 이후엔 Redis 키 쓸 일 없음 → 자동 정리.

---

## 5. EventPublisherPort — 1줄 포트

`application/port/out/EventPublisherPort.java` 전체:

```java
public interface EventPublisherPort {
    void publish(String topic, String key, Object payload);
}
```

### AbstractKafkaPublisher — 공통 발행 로직

`infrastructure/messaging/producer/AbstractKafkaPublisher.java`:

```java
@Override
public void publish(String topic, String key, Object payload) {
    String message = kafkaMessageUtil.serialize(payload);
    kafkaTemplate.send(topic, key, message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka {} 발행 실패 - topic: {}, key: {}", publisherName, topic, key, ex);
                } else {
                    log.debug("Kafka {} 발행 성공 - topic: {}, key: {}", publisherName, topic, key);
                }
            });
}
```

**fire-and-forget**:
- `send()`는 `CompletableFuture` 반환 — **await 하지 않음**.
- `whenComplete`는 로그만 남김. 실패 시 **재전송 없음, DLQ 없음**.
- 장점: 지연 없는 발행. 단점: 메시지 유실 가능 → 모니터링·알람이 안전망.

### 단건 vs 대량 두 Publisher

| 빈 이름 | `@Primary` | 이름 태그 | 용도 |
|---------|-----------|-----------|------|
| `KafkaEventPublisher` | ✅ | "단건" | 대부분의 이벤트 (ticket.paid 등) |
| `KafkaBulkEventPublisher` | ❌ | "대량" | 일일 배치 `ticket.provide` (snappy 압축) |

`KafkaBulkEventPublisher`는 `@Qualifier("bulkKafkaTemplate")`로 별도 KafkaTemplate 주입. 압축·배치 설정은 `KafkaProducerConfig.java`에서 다름.

---

## 6. Kafka 토픽 상수 — 9개

`infrastructure/messaging/KafkaTopics.java`:

```java
// inbound — from movie-service
public static final String SCHEDULE_CONFIRMED = "movie.schedule.confirmed";

// outbound — ticket lifecycle
public static final String TICKET_PAID      = "ticket.paid";
public static final String TICKET_REFUNDED  = "ticket.refunded";
public static final String TICKET_PROVIDE   = "ticket.provide";

// outbound — schedule lifecycle
public static final String CART_CLOSED       = "cart.closed";
public static final String TICKETING_STARTED = "ticketing.started";

// outbound — review
public static final String REVIEW_AUTHORIZED = "ticket.review.authorized";

// internal — queue management
public static final String QUEUE_DRAIN      = "queue.drain";
public static final String QUEUE_TERMINATED = "queue.terminated";
```

**구분**:
- **inbound 1개**: movie-service가 "스케줄이 확정됐다"고 알려줌 → Schedule 엔티티 생성.
- **outbound 6개**: 다른 서비스들이 구독. 정산·리뷰·알림 등.
- **internal 2개**: 이 서비스 안에서 스스로 주고받는 이벤트 (`queue.drain`이 핵심).

---

## 7. UserPort — 쿠키 2개 메서드

`application/port/out/UserPort.java`:

```java
public interface UserPort {
    DeductCookieResponse deductTicketFee(DeductCookieRequest request);
    RefundCookieResponse refundCookie(RefundCookieRequest request);
}
```

### UserClient — RestClient 구현

`infrastructure/client/user/UserClient.java` 발췌:

```java
@Override
public DeductCookieResponse deductTicketFee(DeductCookieRequest request) {
    try {
        return userRestClient.post()
                .uri("/internal/users/deduct/cookie")
                .body(request)
                .retrieve()
                .body(DeductCookieResponse.class);
    } catch (HttpClientErrorException e) {
        // 4xx: 잔액 부족(402), 유저 없음(404) 등 — 차감 실패로 처리
        return new DeductCookieResponse(null, null, null, false);
    } catch (RestClientException e) {
        throw new RuntimeException(e);
    }
}
```

**주목할 점**:
- **4xx = 비즈니스 실패** → `flag=false` 정상 응답으로 변환. Service는 예외 처리 안 하고 `if (!flag)` 분기.
- **5xx/네트워크 실패 = 시스템 오류** → `RuntimeException` 래핑. Service 예외 전파 → 트랜잭션 롤백 + `CookieCompensationHelper` 훅 발동 (한 방에 끝나지는 않음, deduct 이전이면 롤백할 게 없음).
- **retry/circuit breaker 없음**. 일시적 장애도 유저 실패로 보임 → 실운영이라면 Resilience4j 같은 도구 고려.

---

## 8. SchedulerPort — Quartz 래퍼

`application/port/out/SchedulerPort.java`:

```java
public interface SchedulerPort {
    void scheduleCartCloseJob(Long scheduleId, LocalDateTime triggerTime);
    void scheduleTicketingStartJob(Long scheduleId, LocalDateTime triggerTime);
    void scheduleReviewAuthJob(Long scheduleId, LocalDateTime triggerTime);
    void scheduleStreamingStartJob(Long scheduleId, LocalDateTime triggerTime);
    void scheduleStreamingFinishJob(Long scheduleId, LocalDateTime triggerTime);
    void cancelScheduledJobs(Long scheduleId);
}
```

### QuartzSchedulerAdapter — Job/Trigger 생성 핵심

`infrastructure/scheduling/QuartzSchedulerAdapter.java:57-83`:

```java
private void scheduleJobForSchedule(Class<? extends QuartzJobBean> jobClass, String jobType,
                                    Long scheduleId, LocalDateTime triggerTime) {
    String group = jobType.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    String jobName = jobType + "Job_" + scheduleId;
    String triggerName = jobType + "Trigger_" + scheduleId;

    JobDetail job = JobBuilder.newJob(jobClass)
            .withIdentity(jobName, group)
            .usingJobData(SCHEDULE_ID_KEY, scheduleId)
            .storeDurably()
            .build();

    Date startAt = Date.from(triggerTime.atZone(ZoneId.systemDefault()).toInstant());
    Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerName, group)
            .startAt(startAt)
            .withSchedule(SimpleScheduleBuilder.simpleSchedule())
            .build();

    try {
        scheduler.scheduleJob(job, trigger);
    } catch (SchedulerException e) {
        throw new RuntimeException("Quartz job 등록 실패: " + jobType + ", scheduleId=" + scheduleId, e);
    }
}
```

**주목할 점**:
- **`jobName = {type}Job_{scheduleId}`** → 스케줄마다 고유. 같은 Job이 두 번 등록되면 `SchedulerException` (중복 방지).
- **`usingJobData(SCHEDULE_ID_KEY, scheduleId)`** — Job 실행 시 이 파라미터로 어떤 스케줄의 Job인지 식별 (JobExecutionContext).
- **`storeDurably()`** — Trigger가 삭제돼도 JobDetail은 남음. 재설정 가능.
- **Group 네이밍 규칙**: `"CartClose"` → `"CART_CLOSE"`. 로그·Quartz 관리 UI에서 읽기 쉽게.

---

## 9. Batch 포트 2종 — 동기 실행

`TicketProvideBatchPort`:
```java
public interface TicketProvideBatchPort {
    boolean run(); // true=COMPLETED, false=FAILED/예외
}
```

`TicketCleanupBatchPort`:
```java
public interface TicketCleanupBatchPort {
    void run(Long scheduleId); // 미결제 RESERVED 티켓 일괄 DELETE
}
```

### 왜 동기인가?
- `TicketCleanupBatchAdapter.run(scheduleId)`이 **완료된 후에야** TICKETING 전이 가능 (Stage 2 참조).
- 완료 여부를 **return 값 / 예외**로 받아야 → 동기 실행 (`JobOperator.start()` + 폴링 or join).
- 트레이드오프: 배치 오래 걸리면 Quartz 스레드 점유 → Stage 6에서 상세.

---

## 10. 어댑터 교체 사고실험 — Redis → Caffeine?

- **불가능한 부분**: `popMinFromZSet` → Caffeine은 로컬 인메모리라 **프로세스 간 공유 안 됨**. 분산 환경에서 의미 없음.
- **가능한 부분**: `get/set`, TTL, 단순 counter → Caffeine으로 대체 가능.

**결론**: 이 서비스는 **대기열·분산 카운터에 Redis 원자 연산이 필수** → 포트 이름은 `CachePort`지만 **실질적으로는 Redis에 강결합**. 현실에서는 흔한 타협.

---

## 11. 설계 교훈 3가지

### (1) 포트는 "도메인 언어", 어댑터는 "인프라 언어"
- `UserPort.deductTicketFee(...)` → 도메인은 "쿠키를 차감한다"만 앎.
- `UserClient.deductTicketFee(...)` → HTTP URL, 4xx 변환 등 인프라 처리.

### (2) fire-and-forget에는 모니터링이 안전망
- `AbstractKafkaPublisher.whenComplete`가 로그만 남김.
- **실운영**: 로그 alerting(Loki, Datadog), 또는 DLQ 토픽 추가.

### (3) 4xx vs 5xx 구분으로 "비즈니스 실패"와 "시스템 오류"를 분리
- 4xx → `flag=false` 정상 응답 → 서비스는 비즈니스 분기.
- 5xx → 예외 → 트랜잭션 롤백.

---

## ★ 핵심 질문

1. ★ Redis 7키 각각의 (생성 / 읽기 / 쓰기 / 만료) 시점을 표로 그려 보라. 섹션 4 표를 가리지 말고.
2. ★ `KafkaEventPublisher`가 `kafkaTemplate.send(...)` 후 `whenComplete`에서 예외를 받으면 어떻게 되는가? 메시지는 어디로?
3. `UserClient.deductTicketFee`가 **네트워크 타임아웃**을 만나면 어떤 경로로 예외가 전파되는가? (`catch` 블록 어느 쪽?)
4. `QuartzSchedulerAdapter.scheduleJobForSchedule`의 `storeDurably()`가 의미하는 바는? 제거하면 어떤 차이가?
5. 왜 `TicketProvideBatchPort.run()`은 `boolean`을 반환하고 `TicketCleanupBatchPort.run()`은 `void`인가? (힌트: 실패 시 Service의 반응 차이)

### 1.Redis 7키 Lifecycle (생성·읽기·쓰기·만료)
| 키 명칭 | 생성/초기화 (SET) | 읽기 (GET/RANK) | 주요 쓰기 (INCR/DECR) | 만료/삭제 (TTL/DEL) |
|:---:|:---:|:---:|:---:|:---:|
| **`cart:count`** | 최초 Cart 추가 시 | `getCartCount()` | 장바구니 변경 시 INCR/DECR | CartClose 시 수동 DEL |
| **`stock`** | TicketingStart 시 | `drainQueue` 시 | **DECR** (티켓 발급), **INCR** (환불) | 상영 10분 전 자동 만료 |
| **`queue`** | 유저 입장 시 (ZADD) | `drainQueue` 시 | **ZPOPMIN** (순차 드레인) | 대기열 종료 시 수동 DEL |
| **`paying`** | TicketingStart 시 (0) | `checkTermination` | **INCR** (결제 시작), **DECR** (종료) | 상영 10분 전 자동 만료 |
| **`seats`** | TicketingStart 시 | `computeTicketNum` | - (변경 없음) | 상영 10분 전 자동 만료 |
| **`cookie`** | TicketingStart 시 | (참조용) | - (변경 없음) | 상영 10분 전 자동 만료 |
| **`startTime`** | TicketingStart 시 | 입장 가능 여부 확인 | - (변경 없음) | 상영 10분 전 자동 만료 |

```
2. `send()` 호출 후 비동기적으로 결과를 기다리며, 예외 발생 시 에러 로그(`log.error`)만 남김
   지금은 별도의 재시도나 DLQ가 없음으로 DLQ 생성 또는 로그 모니터링 시스템을 통한 실패 로그를 감지하고 수동 대응 필요
3.`UserClient` 내부의 `try-catch` 구조에 따른 예외 처리
4. Job을 실행할 Trigger가 현재 없더라도 JobDetail 정보를 삭제하지 말고 보관함
5. Batch 포트 반환
 - `TicketProvideBatchPort.run()`은 일일 정산 작업 임으로 성공/실패 여부를 리턴받아 별도로 후처리가 필요
 - `TicketCleanupBatchPort.run()`은 티켓팅 시작 전 "미결제 티켓 정리"가 필수, 성공하면 다음 로직으로 넘어가고, 실패하면 예외를 던져 작업 중지 해야함
```

---

## 체크리스트

- [ ] Redis 키 7개 이름(`stock`, `queue`, `paying`, `seats`, `cookie`, `startTime`, `cart:count`)과 각 키의 "세팅 시점"을 외웠다
- [ ] Kafka 토픽 9개 중 **inbound 1개 / outbound 6개 / internal 2개** 분류를 설명할 수 있다
- [ ] `AbstractKafkaPublisher.publish`가 fire-and-forget이며 DLQ가 없음을 코드로 확인했다
- [ ] `UserClient`가 4xx와 5xx를 **어떻게 다르게 다루는지** 두 catch 블록으로 구분해 설명할 수 있다
- [ ] `QuartzSchedulerAdapter.scheduleJobForSchedule`의 jobName 네이밍이 "스케줄 per 1개 Job"을 보장함을 이해했다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/application/port/out/*.java`
- `src/main/java/com/example/ticketservice/infrastructure/caching/adapter/RedisCacheAdapter.java`
- `src/main/java/com/example/ticketservice/infrastructure/messaging/producer/AbstractKafkaPublisher.java`, `KafkaEventPublisher.java`, `KafkaBulkEventPublisher.java`
- `src/main/java/com/example/ticketservice/infrastructure/client/user/UserClient.java`
- `src/main/java/com/example/ticketservice/infrastructure/scheduling/QuartzSchedulerAdapter.java`
- `src/main/java/com/example/ticketservice/application/constants/RedisKeys.java`
- `src/main/java/com/example/ticketservice/infrastructure/messaging/KafkaTopics.java`
- `docs/reference/infra/01-redis-keys-and-ttl.md` — 공식 키 규칙
- `docs/reference/infra/03-kafka-topics-reference.md` — 토픽 페이로드 명세

← 이전: [Stage 2 — 애플리케이션 서비스](stage-02-application-services.md)
→ 다음: [Stage 4 — 이벤트 리스너 (AFTER_COMMIT)](stage-04-event-listeners.md)