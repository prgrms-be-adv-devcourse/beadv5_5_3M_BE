# 아키텍처

## 목차

- [패키지 구조](#패키지-구조)
- [서비스 경계](#서비스-경계)
- [데이터베이스 스키마](#데이터베이스-스키마)
- [Kafka 이벤트 처리](#kafka-이벤트-처리)
- [장애 방어 전략](#장애-방어-전략)

---

## 패키지 구조

```
src/main/java/com/example/aiservice/
├── presentation/               # 외부 요청 진입점
│   ├── controller/             # REST API 컨트롤러
│   └── dto/                    # 요청/응답 DTO
│
├── application/                # 비즈니스 로직
│   ├── usecase/                # UseCase 인터페이스 (Kafka Consumer → Service 진입점)
│   ├── service/                # 서비스 구현체
│   └── batch/                  # 자정 배치 처리
│
├── domain/                     # 도메인 모델
│   ├── model/                  # Entity 및 값 객체
│   └── repository/             # Repository 인터페이스 (포트)
│
├── infrastructure/             # 외부 시스템 어댑터
│   ├── embedding/              # OpenAI 임베딩 클라이언트
│   ├── llm/                    # OpenAI LLM Re-ranking 클라이언트
│   ├── kafka/                  # Kafka Consumer + DTO
│   ├── persistence/            # JPA Repository 구현체
│   └── redis/                  # Redis 클라이언트
│
└── global/                     # 공통 예외 처리, 응답 포맷
```

**Kafka Consumer 흐름**: `Consumer` → `UseCase 인터페이스` → `Service 구현체`

Consumer는 메시지 역직렬화와 예외 격리만 담당합니다. 비즈니스 로직은 Service에 위임합니다.

---

## 서비스 경계

이 서비스는 **추천 계산 데이터만 소유**합니다.

### 소유하는 데이터

| 테이블 | 역할 |
|--------|------|
| `movies_embedded` | 영화 임베딩 벡터 및 요약 (ANN 검색 대상) |
| `movie_statistics` | 인구통계별 시청 카운터 (탐색 후보 선정용) |
| `user_preference` | 유저 취향 클러스터, epsilon |
| `user_interaction_history` | K-Means 입력용 시청·좋아요 기록 |
| `recommended_movie` | 배치 추천 결과 (15개 보관, 10개 반환) |
| `recommended_log` | 노출·클릭 이력 (다양성 관리 + CTR 집계) |

### 소유하지 않는 데이터

| 데이터 | 소유 서비스 | 수신 방법 |
|--------|------------|-----------|
| 유저 계정 정보 | User 서비스 | Kafka `user.created` / `user.deleted` |
| 영화 원본 정보 | Movie 서비스 | Kafka `movie.ai.created` / `movie.ai.updated` / `movie.deleted` |
| 시청 기록 원본 | Ticket 서비스 | Kafka `ticket.review.authorized` |

---

## 데이터베이스 스키마

### movies_embedded

ANN 검색의 대상 테이블. `category`는 임베딩 생성 입력값이자 LLM Re-ranking 프롬프트에도 사용됩니다.

```sql
movie_id      BIGINT PRIMARY KEY
embedding     vector(1536)          -- pgvector, text-embedding-3-small
summary       TEXT                  -- LLM이 생성한 영어 요약
category      TEXT[]                -- LLM이 번역한 영어 카테고리
is_public     BOOLEAN               -- false: ANN 검색 및 추천 대상 제외
published_at  TIMESTAMP             -- 최초 공개 시각 (new_release 탐색 기준), NULL 허용
```

> `is_public=false`인 영화는 배치 추천 후보에서 제외됩니다.
> `published_at`은 최초 공개 시 한 번만 기록되며, 비공개→재공개 시에도 변경되지 않습니다.

### movie_statistics

인구통계 기반 탐색 후보 선정용 카운터 테이블. 영화 1개당 22행(`age_group` × `gender`)으로 초기화됩니다.

```sql
movie_id      BIGINT
age_group     INT                   -- 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100
gender        VARCHAR(10)           -- 'MALE' | 'FEMALE'
watch_count   INT                   -- 해당 인구통계의 unique 시청자 수
PRIMARY KEY (movie_id, age_group, gender)
```

> `watch_count`는 `total_count`(전체 시청 횟수)로 정규화하지 않습니다.
> 대신 배치 쿼리 시점에 `SUM(watch_count) OVER (PARTITION BY movie_id)` 윈도우 함수로 정규화합니다.
> (시청 이벤트마다 22행을 UPDATE하는 비효율을 방지하기 위한 설계입니다.)

### user_preference

유저 취향 클러스터와 탐색 파라미터를 보관합니다.

```sql
user_id                UUID PRIMARY KEY
age_group              INT
gender                 VARCHAR(10)
cluster                JSONB        -- [{"center": [...], "weight": 0.625}, ...] K-Means 결과
watch_count            INT          -- epsilon 갱신 및 cold start 기준 (WATCH 이벤트마다 증가)
updated_at             TIMESTAMP
exploration_click_rate FLOAT        -- 탐색 추천 클릭률
epsilon                FLOAT        -- 탐색 비율 (0~1)
```

> `user.created` 수신 시 INSERT됩니다. Kafka 이벤트 유실에 대비해 lazy creation도 지원합니다.
> ```java
> userPreferenceRepository.findById(userId)
>     .orElseGet(() -> createDefaultPreference(userId));
> ```

### user_interaction_history

K-Means 클러스터 재계산용 인터랙션 기록입니다.

```sql
id                BIGINT PRIMARY KEY (IDENTITY)  -- surrogate PK (재시청 지원)
user_id           UUID
movie_id          BIGINT
interaction_type  VARCHAR(10)     -- 'WATCH' | 'LIKE'
schedule_id       BIGINT          -- WATCH는 scheduleId, LIKE는 NULL
created_at        TIMESTAMP
```

**K-Means 가중치**: WATCH × 1.0, LIKE × 3.0

**좋아요 처리 방식**:
- LIKED 이벤트: `INSERT` (이미 있으면 무시 — 멱등성은 코드 체크로 처리)
- UNLIKED 이벤트: `DELETE WHERE user_id=? AND movie_id=? AND interaction_type='LIKE'`

재시청은 동일 영화에 여러 행이 쌓여 자연스럽게 선호도 강도를 반영합니다.

### recommended_movie

배치 추천 결과를 저장합니다. 15개 보관, API는 상위 10개를 반환합니다.

```sql
movie_id            BIGINT
user_id             UUID
rank                INT
is_exploration      BOOLEAN
exploration_source  VARCHAR(20)   -- 'demographic' | 'new_release' | 'low_exposure'
created_at          TIMESTAMP
PRIMARY KEY (movie_id, user_id)
```

### recommended_log

노출 및 클릭 이력입니다. FK 제약이 없으므로 영화/유저 삭제 시 자동 연계 삭제가 없고, 별도 처리가 필요합니다.

```sql
id                  BIGINT PRIMARY KEY
user_id             UUID
movie_id            BIGINT
is_clicked          BOOLEAN DEFAULT false
is_exploration      BOOLEAN
exploration_source  VARCHAR(20)
recommended_at      DATE
UNIQUE (user_id, movie_id, recommended_at)
```

**두 가지 목적**:
1. **다양성 강제**: 최근 N일 내 노출된 영화를 추천 후보에서 제외 (N은 영화 수 기반 동적 계산)
2. **탐색 CTR 집계**: `epsilon` 갱신에 사용 (90일 고정 윈도우)

> 다양성 윈도우: `clamp(총 영화 수 / 15, 30, 180)` 일수로 동적 계산
> cleanup 기준: `max(diversity_window, ctr_window)`로 자동 파생

---

## Kafka 이벤트 처리

### movie.ai.created

```
movies_embedded   → INSERT (embedding + summary + category, is_public=false)
movie_statistics  → INSERT 22행 초기화
                    age_group {0,10,...,100} × gender {MALE, FEMALE}
                    watch_count=0
```

영화는 생성 직후 `is_public=false`입니다. `movie.ai.updated`의 visibility 변경으로 공개 처리합니다.

### movie.ai.updated (Delta Event)

`changedFields` 배열로 변경된 필드만 전달됩니다.

| changedFields | 처리 내용 |
|---------------|-----------|
| `category` 또는 `description` | 임베딩·요약·category 재생성 (동시 변경 시 1회만 실행) |
| `visibility: PUBLIC→PRIVATE` | `is_public=false` + `recommended_movie` DELETE + Redis 키 DELETE (best effort) |
| `visibility: PRIVATE→PUBLIC` | `is_public=true` + `published_at = NOW()` (최초 공개 시각만 기록) |

> **컨트랙트 규약**: `changedFields`에 `category` 또는 `description`이 포함되면, `description`과 `category` 모두 페이로드에 포함되어야 합니다.

### movie.deleted

```
1. recommended_movie에서 영향받는 userId 목록 조회  ← 삭제 전 먼저 조회
2. movies_embedded + movie_statistics + recommended_movie → hard DELETE (트랜잭션)
3. 영향받는 userId별 Redis 추천 캐시 삭제 (best effort)
```

> `user_interaction_history`는 삭제하지 않습니다. 배치의 embedding-aware cold-start 판단에서 삭제된 영화의 interaction은 자동으로 score에서 제외됩니다.

### movie.liked

```
action: "LIKED"   → user_interaction_history INSERT (이미 있으면 무시)
action: "UNLIKED" → user_interaction_history DELETE
```

> LIKED 처리 시 영화 존재 여부와 무관하게 INSERT를 진행합니다. K-Means는 배치 시점에 embedding-aware cold-start 판단으로 삭제된 영화를 필터링합니다.

### ticket.review.authorized

```
movie 존재 여부 확인 (movieEmbeddedRepository.existsById)
  없으면 → @RetryableTopic 재시도 (30s / 60s / 120s, 3회) → DLT
  있으면 →
    user_interaction_history  → INSERT (user_id, movie_id, 'WATCH', schedule_id)
    movie_statistics          → watch_count 증가 (첫 시청 여부 코드 체크)
    user_preference           → watch_count 증가
```

> **멱등성**: `schedule_id` 기준으로 중복 이벤트를 skip합니다.
> **첫 시청 체크**: `save()` **이전**에 `existsBy` 확인 (이후에 하면 방금 저장한 행이 걸려 항상 false 반환).

### user.created

```
user_preference → INSERT (user_id, age_group, gender, 기본값)
```

> `user.updated`는 없습니다. 연령·성별은 최초 설정 후 변경 불가입니다.

### user.deleted

```
user_preference + user_interaction_history + recommended_movie + recommended_log
  → hard DELETE WHERE user_id = ? (단일 트랜잭션)
```

---

## 장애 방어 전략

| 시나리오 | 대응 방식 | 트레이드오프 |
|---------|-----------|-------------|
| Redis DELETE 실패 | TTL 24시간 만료로 자연 정리 | Eventual Consistency 수용 |
| 추천 결과 7개 이하 | `@Async` 백그라운드 재계산, 트랜잭션 커밋 후 실행 | 다음 요청에서 개선 |
| LLM 호출 실패 | similarity score 내림차순 정렬로 fallback | 추천 품질 일시 저하 |
| user_preference 없음 | lazy creation으로 신규 생성 | Kafka 이벤트 유실 방어 |
| ticket 이벤트 수신 시 movie 미동기화 | `@RetryableTopic` 3회 재시도 후 DLT | 최대 3.5분 지연 |
| Kafka poison pill 메시지 | try-catch로 WARN 로그 + offset commit (skip) | 해당 메시지 영구 유실 |
| 추천 후보 부족 (< 15개) | 다양성 윈도우 완화 후 재시도 (1차: log 제외 완화, 2차: interaction만 제외) | 다양성 감소 허용 |
| OpenAI Batch API 미완료 | 벡터 기반 fallback으로 즉시 저장 | 추천 품질 저하 |
| LLM hallucination (후보 외 movieId 반환) | candidate meta 없는 movieId skip | 최종 추천 수 감소 가능 |
