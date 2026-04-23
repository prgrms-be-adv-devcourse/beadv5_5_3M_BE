# 추천 알고리즘

## 목차

- [개요](#개요)
- [자정 배치 처리 순서](#자정-배치-처리-순서)
- [K-Means 클러스터 재계산](#k-means-클러스터-재계산)
- [Epsilon 갱신 (탐색/착취 비율)](#epsilon-갱신-탐색착취-비율)
- [추천 계산](#추천-계산)
- [추천 API 흐름](#추천-api-흐름)
- [설계 기준값 근거](#설계-기준값-근거)

---

## 개요

이 서비스는 **epsilon-greedy bandit** 전략을 기반으로 합니다.

- **착취(Exploitation)**: 유저의 기존 취향과 유사한 영화를 ANN 벡터 검색으로 추천
- **탐색(Exploration)**: 인구통계 기반, 신작, 저노출 영화를 추천해 새로운 취향 발견 유도

`epsilon` 값(0~1)이 탐색 비율을 결정합니다. 시청 이력이 많은 유저는 epsilon이 낮아져 착취 비중이 높아지고, 신규 유저는 epsilon이 높아져 탐색 위주로 추천됩니다.

---

## 자정 배치 처리 순서

```
00:00  1단계: recommended_log 정리
         DELETE 기준: max(diversity_window, 90일)
         diversity_window = clamp(총 영화 수 / 15, 30, 180)

00:10  2단계: K-Means 클러스터 재계산
         user_interaction_history → 클러스터 중심점 계산
         user_preference.cluster JSONB UPDATE

00:20  3단계: epsilon 갱신
         탐색 클릭률 집계 → epsilon 계산
         user_preference.epsilon UPDATE

00:30  4단계: 추천 계산
         후보 추출 → 필터링 → LLM Re-ranking → recommended_movie UPSERT
```

> 각 단계는 독립적으로 실행되며, 한 단계의 실패가 다음 단계를 막지 않습니다.
> 실패 시 지수 백오프 재시도(30s→60s→120s, 최대 3회)를 수행합니다.

---

## K-Means 클러스터 재계산

### 목적

유저의 취향을 **여러 관심 영역(클러스터)의 중심 벡터**로 표현합니다. 착취 후보 선정 시 각 클러스터 중심점에서 ANN 검색을 수행합니다.

### 처리 흐름

```
user_interaction_history (최근 100개) 조회
    ↓
movies_embedded에서 임베딩 벡터 배치 조회 (IN 쿼리)
    ↓
가중치 적용: WATCH × 1.0, LIKE × 3.0
    ↓
Cold Start 판단: embedding이 존재하는 interaction의 가중치 합계 < 15.0
  → Cold Start: cluster = null로 리셋, 추천 계산에서 탐색 후보만 사용
  → 정상:  K-Means++ 초기화 + 엘보우 메서드로 최적 K 선택
    ↓
user_preference.cluster JSONB UPDATE
```

### K-Means 구현 상세

**K-Means++ 초기화**: 첫 중심점은 랜덤, 이후 중심점은 기존 중심점과 거리²에 비례한 확률로 선택합니다. 일반 랜덤 초기화보다 수렴 속도와 품질이 높습니다.

**엘보우 메서드 (WCSS)**:
```
K=1부터 K_max(5)까지 WCSS 계산
K→K+1 감소폭 / K-1→K 감소폭 < ELBOW_THRESHOLD(0.3)이면 현재 K 선택
```

**청크 처리**: 유저별 반복 쿼리 대신 100명 단위 청크로 묶어 배치 조회합니다.
- 유저별 2N 쿼리 → 청크당 2쿼리로 최적화

**ClusterCenter 구조**:
```java
record ClusterCenter(float[] center, double weight) {}
// user_preference.cluster JSONB로 저장
// [{"center": [0.1, 0.2, ...], "weight": 0.625}, ...]
```

---

## Epsilon 갱신 (탐색/착취 비율)

### Epsilon 공식

```
epsilon = (BASE_EPSILON / (1 + ln(1 + watchCount))) × (1 + explorationClickRate)
```

**두 신호 분리**:
- `watchCount`: 데이터 신뢰도 — 시청 이력이 쌓일수록 epsilon 감소 (착취 비중 증가)
- `explorationClickRate`: 탐색 성향 — 탐색 추천을 많이 클릭할수록 epsilon 증가

> 이전 공식은 `watchCount`만 반영해 "탐색을 좋아하는 유저에게 탐색을 줄이는" 역설이 발생했습니다.

**신규 유저 처리**: `exploration_click_rate`가 없으면 `DEFAULT_EXPLORATION_RATE`(전체 유저 평균 prior) 사용

**CTR 집계 윈도우**: 90일 고정 (유저 행동 데이터 기반, 영화 수와 무관)

---

## 추천 계산

### Cold Start 분기

K-Means 클러스터가 null인 유저(가중치 합계 < 15.0)는 Cold Start입니다.

```
Cold Start 유저:
  탐색 후보만 구성 → demographic(8) + new_release(4) + low_exposure(3) = 15개
  → is_exploration = true 전체 태깅
  → LLM Re-ranking 없이 벡터 기반 즉시 저장

일반 유저:
  착취 후보: 클러스터 중심점별 ANN 검색 (총 30개)
  탐색 후보: demographic(20) + new_release(12) + low_exposure(8) = 40개
  → 필터링 → 중복 제거 → LLM Re-ranking → 15개
```

### 탐색 후보 버킷

| 버킷 | 선정 기준 | 목적 |
|------|-----------|------|
| `demographic` | `movie_statistics`의 연령/성별 비율 상위 | 비슷한 인구통계의 선호 반영 |
| `new_release` | `movies_embedded.published_at` 최신 | 신작 노출 |
| `low_exposure` | `movie_statistics`의 `watch_count` 하위 | 인기 편향(Popularity Bias) 방지 |

### 필터링

LLM Re-ranking 전에 아래 영화를 후보에서 제거합니다.

1. `user_interaction_history`에 있는 영화 (이미 시청·좋아요한 영화)
2. `recommended_log` 다양성 윈도우 내 노출된 영화
3. `movies_embedded.is_public = false` 영화

**후보 부족 시 다양성 윈도우 완화**:
- 후보 수 < 15개: recommended_log 제외 완화 후 재시도
- 1차 완화 후에도 부족: interaction 기록 영화만 제외 (log 제외 없음)

### 중복 제거

착취·탐색 후보에 동일 `movie_id`가 있으면 `is_exploration=false`(착취) 우선, 최대 70개

### LLM Re-ranking

```
후보 70개 → gpt-4o-mini → 상위 15개 movie_id 반환
```

**프롬프트 입력**: `[movieId] [카테고리] summary 한 줄` 형태로 후보 70개 나열

**토큰 사용량**: 약 4,000~5,000 토큰/요청
- system prompt ~90 + user profile ~20 + 후보 70개 × ~60 토큰 + 출력 ~50
- description 전문 사용 시(70개 × ~350 토큰 ≈ 24,500) 대비 약 1/5 수준

**실패 Fallback**: similarity score 내림차순 정렬
- `rerankImmediate()`(추천 결과 7개 이하 시 즉시 재계산 경로)에만 연결·읽기 각 5초 timeout 적용
- 5초 초과 시 LLM 호출 포기 → 벡터 유사도 순으로 fallback
- Batch API 제출/수령용 RestClient는 timeout 없음 (장시간 대기가 정상 동작)

**OpenAI Batch API 활용**:
- 00:30에 전체 유저 추천 요청을 한 번에 제출
- 26시간 후(다음 날 02:00) 결과 수령
- 미완료 또는 Redis TTL 만료 시: 벡터 기반 fallback으로 즉시 계산

### recommended_movie 저장

```
deleteByUserId(userId) + saveAll(movies)  -- UPSERT 방식
```

> 배치는 Redis에 쓰지 않습니다. Redis 캐싱은 API 응답 시점에만 수행합니다.
> 
> (이유: 배치 시점에는 recommended_log.id(logId)가 없어 캐시 포맷 불일치 발생)

---

## 추천 API 흐름

### GET /recommendations

```
1. Redis 조회 (키: recommendations:{userId})
   Cache Hit  → [logId, movieId] 목록 즉시 반환
   Cache Miss → recommended_movie SELECT (LIMIT 10)
              → recommended_log INSERT ON CONFLICT DO NOTHING
              → logId 포함해서 Redis 캐싱 (TTL: 다음 자정까지)
              → 결과 반환

2. 결과 7개 이하:
   → Redis 캐싱 생략 (캐싱하면 재계산 후에도 stale 결과가 히트됨)
   → 트랜잭션 커밋 후 백그라운드 재계산 트리거 (@Async)

3. 응답 형식: [{logId, movieId}, ...]
   (logId: recommended_log.id — 클릭 이벤트 전송 시 사용)
```

**Redis 캐시 설계**:
- 캐시 키: `recommendations:{userId}`
- 캐시 포맷: `[{logId, movieId}]` JSON 문자열
- TTL: 다음 자정까지 남은 시간 (초 단위)

### PATCH /recommendations/click

```json
{ "log_id": 123 }
```

```sql
UPDATE recommended_log
SET is_clicked = true
WHERE id = ? AND user_id = ?  -- user_id 검증 필수 (BOLA 방어)
```

---

## 설계 기준값 근거

### Cold Start 임계값: 15.0 (가중치 합계)

- K-Means 안정적 동작을 위해 클러스터당 최소 20개 포인트 필요
- K_max=5로 설정 시 최소 데이터: 5 × 3편 = 15편
- LIKE × 3.0, WATCH × 1.0 가중치 기준으로 15.0 이상이면 K-Means 실행

### K_max=5 (최대 클러스터 수)

세 방향의 근거가 일치합니다.

1. **문헌**: Adomavicius & Tuzhilin (2005) — 유저 관심사는 보통 2~5개 영역으로 수렴
2. **수학적 도출**: `K_max = 윈도우 크기 / 최소 포인트 수 = 100 / 20 = 5`
3. **도메인 직관**: 영화 대분류 카테고리가 5개 내외

실제로는 엘보우 메서드가 대부분 K=2~3을 선택합니다. K=5는 상한선입니다.

### 윈도우 크기 N=100

- **기간 기반 대신 개수 기반 채택**: 비활성 유저(월 1회 방문)는 기간 기반 시 데이터가 극단적으로 적어져 클러스터링 불안정
- **N=100 근거**: Covington et al. (2016, YouTube DNN) — 최근 시청 50~200개 사용. 100은 해당 범위 중간값

### 탐색 비중 계산 (epsilon → 후보 수)

```
착취 후보: 30개 × (1 - epsilon)
탐색 후보: 40개 × epsilon
```

epsilon=0.1(신규) → 착취 27개 + 탐색 4개
epsilon=0.5(중간) → 착취 15개 + 탐색 20개
