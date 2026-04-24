# 트러블슈팅

개발 중 발생한 버그와 해결 과정을 기록합니다.

**읽는 순서**: Spring 핵심 원칙 → Kafka 수신 레이어 → 배치 레이어 → DB/JPA 레이어 → API/캐시 레이어

---

## 목차

**Spring 핵심 함정**
1. [@Transactional self-invocation — BatchPreprocessingService 분리](#1-transactional-self-invocation--batchpreprocessingservice-분리)
2. [@Async self-invocation — RecommendationTrigger 분리](#2-async-self-invocation--recommendationtrigger-분리)
3. [@Async + @Transactional 조합 경쟁 조건 — handleUserCreated](#3-async--transactional-조합-경쟁-조건--handleusercreated)

**Kafka Consumer 레이어**
4. [Kafka Poison Pill 메시지 처리](#4-kafka-poison-pill-메시지-처리)

**배치 레이어**
5. [BatchScheduler 독립 실행 + 지수 백오프 재시도](#5-batchscheduler-독립-실행--지수-백오프-재시도)

**DB/JPA 레이어**
6. [movie.updated embedding save + publishedAt 보존](#6-movieupdated-embedding-save--publishedat-보존)

**API/캐시 레이어**
7. [getRecommendations 백그라운드 트리거 race condition](#7-getrecommendations-백그라운드-트리거-race-condition)
8. [추천 결과 7개 이하 시 Redis 캐싱 생략 이유](#8-추천-결과-7개-이하-시-redis-캐싱-생략-이유)
9. [Redis DELETE best-effort — movie 삭제/비공개 전환 시](#9-redis-delete-best-effort--movie-삭제비공개-전환-시)

---

## 1. @Transactional self-invocation — BatchPreprocessingService 분리

### 증상

`BatchScheduler`에서 `cleanupRecommendedLog()`와 `updateEpsilon()` 메서드에 `@Transactional`을 붙였지만 트랜잭션이 동작하지 않았습니다.

### 원인

Spring `@Transactional`은 **프록시** 기반으로 동작합니다. 같은 클래스 내부에서 메서드를 직접 호출하면 프록시를 거치지 않아 AOP 인터셉터가 적용되지 않습니다.

```java
// BatchScheduler 내부 — 이 호출은 프록시를 우회함
this.cleanupRecommendedLog(); // @Transactional 미동작
```

### 해결

`cleanupRecommendedLog`와 `updateEpsilon`을 `BatchPreprocessingService`라는 **별도 Spring Bean**으로 분리했습니다. `BatchScheduler`에서 주입받아 호출하면 프록시를 거치므로 `@Transactional`이 정상 동작합니다.

```java
// 수정 후
@Service
public class BatchPreprocessingService {
    @Transactional
    public void cleanupRecommendedLog() { ... }

    @Transactional
    public void updateEpsilon(List<UUID> userIds) { ... }
}

@Component
public class BatchScheduler {
    private final BatchPreprocessingService batchPreprocessingService; // 주입받아 호출
}
```

**같은 원칙**: `private` 메서드에는 `@Transactional`이 동작하지 않습니다 (프록시가 오버라이드할 수 없으므로).

---

## 2. @Async self-invocation — RecommendationTrigger 분리

### 증상

`RecommendationService` 내부에서 `@Async` 메서드를 직접 호출했을 때 비동기로 실행되지 않고 동기로 실행되었습니다.

### 원인

`@Async`도 프록시 기반입니다. self-invocation 시 프록시를 우회해 일반 메서드처럼 실행됩니다.

### 해결

비동기 재계산 로직을 `RecommendationTrigger`라는 별도 컴포넌트로 분리했습니다.

```java
@Component
public class RecommendationTrigger {
    @Async
    public void trigger(UUID userId) {
        recommendationCalculationService.calculateWithVectorFallback(userId);
    }
}

@Service
public class RecommendationService {
    private final RecommendationTrigger recommendationTrigger; // 주입받아 호출
}
```

**요약**: `@Transactional`과 `@Async` 모두 같은 클래스 내 self-invocation에서는 동작하지 않습니다. 별도 Bean 분리가 표준 해결책입니다.

---

## 3. @Async + @Transactional 조합 경쟁 조건 — handleUserCreated

### 증상

`user.created` 이벤트 수신 시 `handleUserCreated()`가 `UserPreference`를 INSERT한 후 즉시 Cold Start 추천을 @Async로 트리거했습니다. 그런데 `@Async` 별도 스레드에서 `UserPreference`를 조회하면 null이 반환되는 경우가 발생했습니다.

### 원인

```
메인 스레드:
  @Transactional 시작
    → UserPreference INSERT (DB에 아직 커밋 전)
    → @Async 트리거 (별도 스레드 즉시 시작)
  @Transactional 커밋 ← 이 시점 이후에야 DB에 반영

별도 스레드:
  UserPreference 조회 ← 메인 스레드가 커밋하기 전에 조회 → null
```

트랜잭션이 커밋되기 전에 비동기 스레드가 DB를 읽으면 아직 반영되지 않은 데이터를 보게 됩니다.

### 해결

`TransactionSynchronizationManager.afterCommit()` 콜백을 사용해 **트랜잭션 커밋 완료 후** 비동기 작업을 시작합니다.

```java
@Transactional
public void handleUserCreated(UserCreatedMessage message) {
    UserPreference preference = createDefaultPreference(message);
    userPreferenceRepository.save(preference);

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            recommendationTrigger.trigger(preference.getUserId()); // 커밋 후 실행
        }
    });
}
```

이 패턴은 `@Transactional + @Async` 조합의 표준 해결책입니다. 동일한 패턴이 `getRecommendations`의 백그라운드 재계산에도 적용되었습니다([#7 참고](#7-getrecommendations-백그라운드-트리거-race-condition)).

---

## 4. Kafka Poison Pill 메시지 처리

### 배경

파싱할 수 없는 메시지(poison pill)가 Kafka에 들어오면 Consumer가 해당 메시지에서 반복 실패해 뒤에 쌓인 메시지를 처리하지 못합니다.

### 해결

모든 Consumer 메서드에 `try-catch`를 추가해 파싱 에러 발생 시 WARN 로그만 남기고 offset을 commit(skip)합니다.

```java
@KafkaListener(topics = "user.created")
public void handleUserCreated(String message) {
    try {
        UserCreatedMessage event = objectMapper.readValue(message, UserCreatedMessage.class);
        userPreferenceUseCase.handleUserCreated(event);
    } catch (Exception e) {
        log.warn("[UserEventConsumer] user.created 처리 실패, skip: {}", e.getMessage());
    }
}
```

**트레이드오프**: 해당 메시지는 영구 유실됩니다. 중요 이벤트의 경우 DLT(Dead Letter Topic) 전송을 고려해야 합니다. 현재는 `ticket.review.authorized`만 `@RetryableTopic` + DLT를 사용합니다.

---

## 5. BatchScheduler 독립 실행 + 지수 백오프 재시도

### 배경

배치의 각 단계(cleanup → kmeans → epsilon → calculate)는 순서가 있지만, 한 단계 실패가 이후 단계를 완전히 막으면 안 됩니다.

예: cleanup 실패 → kmeans와 epsilon은 정상 실행해야 함

### 해결

두 가지 헬퍼 메서드를 도입했습니다.

```java
// 실패 시 지수 백오프 재시도 (30s→60s→120s, 최대 3회)
private void executeWithRetry(String name, Runnable task) { ... }

// 단순 격리 실행 (내부 fallback 있는 경우)
private void execute(String name, Runnable task) { ... }
```

```java
@Scheduled(cron = "0 0 * * *")
public void runDailyBatch() {
    executeWithRetry("cleanup", batchPreprocessingService::cleanupRecommendedLog);
    executeWithRetry("kmeans", () -> kMeansClusteringService.recalculateAll());
    executeWithRetry("epsilon", batchPreprocessingService::updateEpsilonForAll);
    execute("calculate", () -> recommendationCalculationService.calculateForAll()); // 내부 fallback 존재
}
```

**스케줄링 스레드 풀 크기 2 설정**:
```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2  # 00:00 submit + 02:00 receive 두 스케줄러가 서로 블로킹하지 않도록
```

---

## 6. movie.updated embedding save + publishedAt 보존

### 배경

`movie.ai.updated`로 category 또는 description이 변경되면 임베딩을 재생성해야 합니다.

### 문제

`@Query` JPQL로 vector 타입 컬럼을 업데이트하면 pgvector 타입 바인딩이 불안정합니다.

```java
// 불안정
@Modifying
@Query("UPDATE MovieEmbedded m SET m.embedding = :embedding WHERE m.movieId = :movieId")
void updateEmbedding(@Param("movieId") Long movieId, @Param("embedding") PGvector embedding);
```

### 해결

기존 엔티티를 로드한 후 rebuild + save 방식을 사용합니다.

```java
MovieEmbedded existing = movieEmbeddedRepository.findById(movieId).orElseThrow();
MovieEmbedded updated = existing.toBuilder()
    .embedding(newEmbedding)
    .summary(newSummary)
    .category(newCategory)
    .publishedAt(existing.getPublishedAt()) // 반드시 보존
    .build();
movieEmbeddedRepository.save(updated); // PK 존재 시 UPDATE
```

**`publishedAt` 보존 필수**: 새 객체를 빌드할 때 기존 `publishedAt`을 복사하지 않으면 null로 덮어써집니다. `publishedAt`은 최초 공개 시각으로 new_release 탐색 기준에 사용되므로 유실되면 안 됩니다.

### visibility public→private 처리

```java
@Transactional
public void handleVisibilityPrivate(Long movieId) {
    // 트랜잭션 내: DB 처리
    movieEmbeddedRepository.setPublicFalse(movieId);
    List<UUID> affectedUserIds = recommendedMovieRepository.findUserIdsByMovieId(movieId);
    recommendedMovieRepository.deleteByMovieId(movieId);

    // 트랜잭션 커밋 후: Redis 삭제 (best effort)
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            affectedUserIds.forEach(redisClient::deleteCache);
        }
    });
}
```

Redis 삭제를 트랜잭션 내부에서 하면 DB rollback 시 Redis만 삭제된 불일치가 발생합니다. 커밋 후 삭제가 정석입니다.

---

## 7. getRecommendations 백그라운드 트리거 race condition

### 증상

추천 결과가 7개 이하일 때 백그라운드 재계산을 트리거하는데, 재계산이 완료되어도 다음 요청에서 여전히 이전 (7개 이하) 결과를 읽는 경우가 발생했습니다.

### 원인

`getRecommendations()`는 `@Transactional` 메서드입니다. 트랜잭션이 커밋되기 전에 `@Async` 백그라운드 스레드를 시작하면, 재계산 스레드가 `recommended_movie`를 업데이트하는 동안 메인 트랜잭션의 읽기 스냅샷이 남아 있어 충돌합니다.

```java
// 잘못된 구현
@Transactional
public List<...> getRecommendations(UUID userId) {
    List<...> result = recommendedMovieRepository.findTop10ByUserId(userId);
    if (result.size() <= 7) {
        recommendationTrigger.trigger(userId); // 트랜잭션 커밋 전에 비동기 시작
    }
    return result;
}
```

### 해결

`TransactionSynchronizationManager.afterCommit()` 콜백으로 이동합니다. (`handleUserCreated`와 동일한 패턴 — [#3 참고](#3-async--transactional-조합-경쟁-조건--handleusercreated))

```java
@Transactional
public List<...> getRecommendations(UUID userId) {
    List<...> result = recommendedMovieRepository.findTop10ByUserId(userId);
    if (result.size() <= 7) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recommendationTrigger.trigger(userId); // 트랜잭션 커밋 후 실행
            }
        });
    }
    return result;
}
```

---

## 8. 추천 결과 7개 이하 시 Redis 캐싱 생략 이유

### 배경

추천 결과가 7개 이하면 부족하다고 판단해 백그라운드 재계산을 트리거합니다. 이때 Redis에 캐싱하면 안 됩니다.

### 이유

7개 이하 결과를 Redis에 캐싱하면:
1. 백그라운드 재계산이 완료되어 `recommended_movie`가 15개로 업데이트됨
2. 그러나 다음 요청에서 Redis Cache Hit → 여전히 7개 이하 결과 반환

재계산 후 새 데이터가 반영되려면 Redis에 Cache Miss 상태를 유지해야 합니다.

```java
if (result.size() <= 7) {
    // Redis 캐싱 생략 — Cache Miss 유지해서 재계산 완료 후 반영되도록
    triggerBackgroundRecalculation(userId);
    return result; // 현재 결과는 그대로 반환 (사용자 경험 유지)
}

// 7개 초과 시에만 캐싱
redisClient.setCache(userId, result);
```

---

## 9. Redis DELETE best-effort — movie 삭제/비공개 전환 시

### 배경

`movie.deleted` 또는 `movie.ai.updated`(비공개 전환) 시 해당 영화가 포함된 유저의 Redis 추천 캐시를 삭제해야 합니다.

### 설계: best-effort

Redis DELETE가 실패해도 예외를 전파하지 않습니다.

```java
try {
    affectedUserIds.forEach(userId ->
        redisClient.deleteCache(userId)
    );
} catch (Exception e) {
    log.warn("[Cache] Redis DELETE 실패, TTL 만료로 자연 정리됨: {}", e.getMessage());
    // 예외 전파 안 함
}
```

**근거**: Redis DELETE 실패는 치명적이지 않습니다. TTL(24시간)이 지나면 자동으로 만료됩니다. 비공개 영화가 최대 24시간 동안 캐시에 남을 수 있지만, DB 조회(Cache Miss) 시에는 `is_public=false` 필터로 제외됩니다.

**주의**: 이 설계는 `is_public=false` 필터가 DB 조회 경로에 항상 적용된다는 전제에 의존합니다. 해당 필터를 제거하면 비공개 영화가 노출될 수 있습니다.
