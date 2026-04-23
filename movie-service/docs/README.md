# Movie Service - Elasticsearch 검색 기능

## 개요

movie-service에 **Elasticsearch + Redis** 기반 검색 기능을 추가했습니다.

| 기술 | 용도 |
|------|------|
| Elasticsearch | 영화 통합 검색, 카테고리 필터, 자동완성, 필터 카운트(Aggregation) |
| Redis (Sorted Set) | 인기 검색어 랭킹 |
| Kafka | RDB ↔ ES 색인 동기화 (이벤트 기반) |

### 기능 ON/OFF

`movie.elasticsearch.enabled` 프로퍼티로 **전체 검색 기능을 토글**할 수 있습니다.
`false`이면 ES 관련 Controller, Service, Consumer, RedisConfig 빈이 모두 로드되지 않습니다.

```yaml
movie:
  elasticsearch:
    enabled: true   # false로 바꾸면 ES 검색 기능 전체 비활성화
```

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│  movie-service                                              │
│                                                             │
│  ┌──────────┐    Kafka     ┌─────────────────────┐          │
│  │MovieService├──publish──►│MovieIndexEventConsumer│          │
│  │(RDB CRUD) │             │  (movie.created/     │          │
│  └──────────┘              │   updated/deleted)   │          │
│                            └──────┬──────────────┘          │
│                                   │                         │
│                            ┌──────▼──────────┐              │
│                            │MovieSearchService│              │
│                            │  (ES 색인 관리)  │              │
│                            └──────┬──────────┘              │
│                                   │                         │
│              ┌────────────────────┼────────────────┐        │
│              ▼                    ▼                ▼        │
│     ┌────────────┐      ┌──────────────┐   ┌──────────┐    │
│     │Elasticsearch│      │    Redis     │   │PostgreSQL│    │
│     │(movies 인덱스)│      │(search:rank)│   │(movie_db)│    │
│     └────────────┘      └──────────────┘   └──────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 데이터 흐름

1. **색인 동기화**: MovieService에서 영화 등록/수정/삭제 시 Kafka 이벤트 발행 → MovieIndexEventConsumer가 수신 → ES에 색인 생성/갱신/삭제
2. **검색**: 사용자 검색 요청 → MovieSearchService가 ES에 쿼리 → 결과 반환
3. **인기 검색어**: 검색 시 `@Async`로 Redis Sorted Set에 키워드 점수 +1 기록 → 인기 검색어 조회 시 ZREVRANGE로 Top N 반환

---

## API 명세

### MovieController (`/api/movies`) — 항상 활성화

| Method | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/movies/search?title={keyword}` | **통합 영화 검색** (아래 설명 참고) |
| `POST` | `/api/movies` | 영화 등록 (크리에이터) |
| `GET` | `/api/movies/on-air` | 현재 상영 중 영화 목록 |
| `GET` | `/api/movies/scheduled` | 상영 예정 영화 목록 |
| `GET` | `/api/movies/public` | 전체 공개 영화 목록 |
| `GET` | `/api/movies/{id}/detail` | 영화 상세 조회 |
| `GET` | `/api/movies/list?creatorId={id}` | 크리에이터별 공개 영화 목록 |
| `GET` | `/api/movies/genre/{categoryId}` | 장르별 영화 목록 (JPA) |
| `PATCH` | `/api/movies/{id}/visibility` | 공개/비공개 변경 |
| `PATCH` | `/api/movies/{id}/detail` | 영화 상세 수정 |
| `DELETE` | `/api/movies/{id}` | 영화 삭제 |

#### 통합 검색 (`GET /api/movies/search`)

`movie.elasticsearch.enabled` 설정에 따라 **자동 전환**:

| 설정 | 검색 방식 | 검색 대상 | 정렬 |
|------|-----------|-----------|------|
| `enabled: true` | ES (nori 형태소 분석) | 제목 + 설명 + 크리에이터명 | 관련도순 |
| `enabled: false` | JPA (LIKE 검색) | 제목만 | 기본순 |

**프론트엔드는 API 주소 변경 없이 동일하게 호출** — 백엔드 설정만 바꾸면 검색 엔진이 전환됨.

### MovieSearchController (`/api/movies/es`) — ES 활성화 시에만 동작

| Method | 경로 | 설명 | 호출 시점 |
|--------|------|------|-----------|
| `GET` | `/api/movies/es/category/{categoryId}?page=&size=` | 카테고리별 영화 검색 | 카테고리 필터 클릭 시 |
| `GET` | `/api/movies/es/autocomplete?prefix=&size=` | 자동완성 | 유저 타이핑 중 (실시간) |
| `GET` | `/api/movies/es/popular?size=` | 인기 검색어 Top N | 페이지 처음 열 때 |
| `GET` | `/api/movies/es/filters` | 카테고리별/평점별 영화 수 집계 | 페이지 처음 열 때 |
| `POST` | `/api/movies/es/index/{movieId}` | ES 색인 생성 (관리용) | Kafka 또는 수동 |
| `DELETE` | `/api/movies/es/index/{movieId}` | ES 색인 삭제 (관리용) | Kafka 또는 수동 |

#### 검색 페이지에서 API 호출 흐름

```
유저가 검색 페이지 진입
  ├→ GET /api/movies/es/popular        → 인기 검색어 표시
  └→ GET /api/movies/es/filters        → 카테고리별 영화 수 표시

유저가 "인터" 타이핑 중 (Enter 전)
  └→ GET /api/movies/es/autocomplete   → 드롭다운 추천

유저가 Enter (검색 실행)
  └→ GET /api/movies/search            → 검색 결과 (ES 또는 JPA)

유저가 카테고리 "SF" 클릭
  └→ GET /api/movies/es/category/3     → SF 영화만 표시
```

---

## ES 인덱스 구조

인덱스명: `movies`

| 필드 | ES 타입 | 설명 |
|------|---------|------|
| id | - | movieId를 문자열로 변환 (@Id) |
| movieId | Keyword | 원본 영화 ID (RDB 참조용) |
| title | Text (standard) | 전문 검색 대상 |
| description | Text (standard) | 전문 검색 대상 |
| creatorId | Keyword | 크리에이터 UUID |
| creatorNickname | Text (standard) | 크리에이터명 검색용 |
| categoryIds | Keyword | 카테고리 ID 필터용 |
| categoryNames | MultiField (Text + Keyword) | 검색용(Text) + 집계용(Keyword) |
| averageRating | Float | 평균 평점 |
| reviewCount | Integer | 리뷰 수 |
| visibility | Keyword | PUBLIC / PRIVATE |
| totalCookie | Integer | baseCookie + additionalCookie |
| runningTime | Integer | 상영 시간 |
| updatedAt | Date | 수정일시 |

---

## Kafka 이벤트 연동 (ES 색인 동기화)

Creator 서비스가 발행하는 Kafka 이벤트를 수신하여 ES 색인을 자동 동기화합니다.
MSA 구조로 Creator 서비스 DB에 직접 접근 불가 → 메시지 데이터로 ES 색인 처리.

```
Creator 서비스 (영화 등록/수정/삭제/공개상태변경)
    │
    ├── movie.uploaded   ──► MovieIndexEventConsumer ──► ES 색인 생성
    ├── movie.updated    ──► MovieIndexEventConsumer ──► ES 색인 업데이트
    ├── movie.deleted    ──► MovieIndexEventConsumer ──► ES 색인 제거
    └── movie.visibility ──► MovieIndexEventConsumer ──► ES visibility 업데이트
```

**토픽별 메시지 포맷** (팀 표준 토픽명: `movie.*`)

| 토픽 | 메시지 | ES 동작 |
|------|--------|---------|
| `movie.uploaded` | `MovieUploadedMessage(movieId, title, description, creatorId, creatorNickname, categories)` | 색인 생성 |
| `movie.updated` | `MovieUpdatedMessage(movieId, title, description, categories)` | 색인 업데이트 |
| `movie.deleted` | `MovieDeletedMessage(movieId)` | 색인 제거 |
| `movie.visibility` | `MovieVisibilityChangedMessage(movieId, visibility)` | PUBLIC ↔ PRIVATE |

**Consumer Group**: `movie-search-service` — 팀 `MovieEventConsumer(movie-service)`와 **같은 토픽을 다른 groupId로** 구독하므로 **서로 영향 없이 전체 메시지 독립 수신**.

**에러 처리 전략** (MovieIndexEventConsumer)
- JSON 파싱 실패 → 비복구성이므로 로그 후 스킵 (재시도해도 동일 결과)
- 비즈니스 로직 실패 (ES 연결 오류 등) → 예외 전파 → Spring Kafka 재시도

---

## 설정

### 로컬 환경 세팅 (최초 1회)

```bash
# 1. Elasticsearch 띄우기
cd local/elasticsearch
docker compose up -d

# 2. movie-db 생성 + 권한 부여 (스크립트 사용)
bash local/elasticsearch/init-movie-db.sh

# 또는 수동으로:
docker exec my-postgres psql -U postgres -c "CREATE DATABASE \"movie-db\";"
docker exec my-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE \"movie-db\" TO \"user\";"
docker exec my-postgres psql -U postgres -d "movie-db" -c "GRANT ALL ON SCHEMA public TO \"user\";"
docker exec my-postgres psql -U postgres -d "movie-db" -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"user\";"

# 3. IntelliJ에서 movie-service 실행 (JPA가 테이블 자동 생성)
```

> **참고**: Redis, PostgreSQL(my-postgres)이 Docker에서 실행 중이어야 합니다.

### 로컬 (application-dev.yaml)

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
  data:
    redis:
      host: localhost
      port: 6379

movie:
  elasticsearch:
    enabled: true
```

### 프로덕션 (application-prod.yaml)

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

# ES URI는 spring.elasticsearch.uris 환경변수로 주입
```

### 필요 의존성 (build.gradle)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

---

## 관련 파일 목록

```
movie-service/src/main/java/com/example/movieservice/
├── application/
│   ├── usecase/MovieSearchUseCase.java          # 검색 유스케이스 인터페이스
│   └── service/MovieSearchService.java          # 검색 구현체 (ES + Redis)
├── infrastructure/
│   ├── elasticsearch/
│   │   ├── document/MovieDocument.java          # ES 문서 매핑
│   │   └── MovieSearchRepository.java           # Spring Data ES Repository
│   ├── redis/
│   │   └── RedisConfig.java                     # Redis 설정
│   └── kafka/
│       ├── consumer/MovieIndexEventConsumer.java # ES 색인 동기화 Consumer
│       ├── producer/KafkaEventPublisher.java     # 이벤트 발행기
│       └── dto/consume/
│           ├── MovieUploadedMessage.java         # movie.uploaded 메시지
│           ├── MovieUpdatedMessage.java          # movie.updated 메시지
│           ├── MovieDeletedMessage.java          # movie.deleted 메시지
│           └── MovieVisibilityChangedMessage.java # movie.visibility 메시지
└── presentation/
    ├── controller/MovieSearchController.java    # 검색 API 컨트롤러
    └── dto/response/movie/
        ├── MovieSearchResponse.java
        ├── MovieSearchItemResponse.java
        ├── AutocompleteResponse.java
        ├── PopularKeywordResponse.java
        └── CategoryFilterCountResponse.java
```
