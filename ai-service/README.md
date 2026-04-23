# AI Recommendation Service

3M팀 CineStream의 AI 추천 서비스입니다. 유저의 시청·좋아요 이력을 학습해 매일 자정 배치로 개인화 추천 목록을 생성합니다.

---

## 목차

- [서비스 소개](#서비스-소개)
- [기술 스택](#기술-스택)
- [사전 요구사항](#사전-요구사항)
- [로컬 구동 방법](#로컬-구동-방법)
- [환경 변수 및 설정](#환경-변수-및-설정)
- [전체 흐름](#전체-흐름)
- [API 목록](#api-목록)
- [문서](#문서)

---

## 서비스 소개

이 서비스는 **추천 계산만 전담**합니다. 유저 계정·영화 원본 정보·시청 기록은 각 서비스가 소유하고, Kafka 이벤트로 AI 서비스에 동기화됩니다.

| 데이터 | 소유 서비스 | AI 서비스 수신 방법 |
|--------|-------------|---------------------|
| 유저 계정 | User 서비스 | `user.created` / `user.deleted` |
| 영화 정보 | Movie 서비스 | `movie.ai.created` / `movie.ai.updated` / `movie.deleted` / `movie.liked` |
| 시청 기록 | Ticket 서비스 | `ticket.review.authorized` |

**추천 결과**는 `[{logId, movieId}]` 형태로만 반환합니다. 제목·이미지 등 표시 정보는 Movie 서비스가 담당합니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.4 |
| Database | PostgreSQL 16 + pgvector |
| Cache | Redis 7 |
| Messaging | Apache Kafka (KRaft 모드) |
| AI | OpenAI `text-embedding-3-small` + `gpt-4o-mini` |
| Build | Gradle |

---

## 사전 요구사항

- Java 21
- PostgreSQL 16 (pgvector 확장 설치 필요)
- Redis 7
- Kafka 브로커 (외부 연결 또는 로컬)
- OpenAI API Key

---

## 로컬 구동 방법

### 1. Redis 실행

```bash
docker-compose up -d
```

`docker-compose.yaml`은 Redis만 포함합니다. Kafka와 PostgreSQL은 별도 설정이 필요합니다.

### 2. PostgreSQL 설정

```sql
CREATE DATABASE ai_db;
\c ai_db

-- pgvector 확장 활성화 (필수)
CREATE EXTENSION vector;
```

### 3. 환경 변수 설정

```bash
export OPENAI_API_KEY=sk-...
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

서버 포트: **8089**
Swagger UI: `http://localhost:8089/swagger-ui.html`

---

## 환경 변수 및 설정

`application-dev.yaml`에서 설정합니다. 주요 항목은 아래와 같습니다.

| 항목 | 기본값 | 설명 |
|------|------|------|
| `OPENAI_API_KEY` | (필수) | OpenAI API 인증 키 |
| `spring.datasource.url` |  | PostgreSQL 연결 URL |
| `spring.kafka.bootstrap-servers` |  | Kafka 브로커 주소 |
| `openai.summary-model` | `gpt-4o-mini` | 영화 요약 생성 모델 |
| `openai.reranking-model` | `gpt-4o-mini` | LLM Re-ranking 모델 |
| `openai.embedding-model` | `text-embedding-3-small` | 임베딩 모델 |
| `openai.embedding-dimensions` | `1536` | 벡터 차원수 |
| `batch.base-epsilon` | `0.1` | 탐색 기본 비율 |
| `batch.kmeans.window-size` | `100` | K-Means 입력 인터랙션 수 |
| `batch.kmeans.k-max` | `5` | K-Means 최대 클러스터 수 |
| `batch.kmeans.cold-start-threshold` | `15.0` | Cold start 판단 가중치 임계값 |

---

## 전체 흐름

```
┌─────────────────────────────────────────────────────────────┐
│                       Kafka 이벤트 수신                       │
│                                                             │
│  Movie 서비스 ──► movie.ai.created  → 임베딩 생성 + 통계 초기화  │
│                   movie.ai.updated  → 임베딩/공개상태 업데이트    │
│                   movie.deleted     → 전체 데이터 삭제          │
│                   movie.liked       → 인터랙션 기록             │
│                                                             │
│  Ticket 서비스 ──► ticket.review.authorized → 시청 기록 저장    │
│                                                             │
│  User 서비스 ──► user.created  → 유저 취향 프로파일 생성         │
│                  user.deleted  → 유저 데이터 전체 삭제           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     자정 배치 (매일 00:00)                    │
│                                                             │
│  00:00  recommended_log 정리 (오래된 노출 기록 삭제)           │
│  00:10  K-Means 클러스터 재계산 (유저 취향 벡터 업데이트)        │
│  00:20  epsilon 갱신 (탐색/착취 비율 조정)                     │
│  00:30  추천 계산                                             │
│           ├── Cold Start 유저: 인구통계·신작·저노출 탐색 후보    │
│           └── 일반 유저: ANN 벡터 검색(착취) + 탐색 후보         │
│                         └── LLM Re-ranking (gpt-4o-mini)    │
│                               └── recommended_movie UPSERT  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     추천 API                                 │
│                                                             │
│  GET /recommendations                                       │
│    ├── Redis 캐시 Hit  → 즉시 반환                            │
│    └── Redis 캐시 Miss → DB 조회 → recommended_log 기록       │
│                           → Redis 캐싱 (TTL: 다음 자정까지)    │
│                           → 결과 7개 이하: 백그라운드 재계산    │
│                                                             │
│  PATCH /recommendations/click                               │
│    └── recommended_log.is_clicked = true                    │
└─────────────────────────────────────────────────────────────┘
```

---

## API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/recommendations` | 유저 추천 목록 조회 (캐시 우선) |
| `PATCH` | `/recommendations/click` | 추천 클릭 이벤트 기록 |

> Swagger UI에서 전체 스펙 확인: `http://localhost:8089/swagger-ui.html`

### 개발용 배치 수동 트리거 (dev 프로파일 전용)

| 메서드 | 경로 | 설명                     |
|--------|------|------------------------|
| `POST` | `/internal/batch/cleanup` | recommended_log 정리     |
| `POST` | `/internal/batch/epsilon` | epsilon 갱신             |
| `POST` | `/internal/batch/kmeans` | K-Means 재계산            |
| `POST` | `/internal/batch/calculate` | 추천 후보 선정 + OpenAI Batch API 제출 |
| `POST` | `/internal/batch/submit-daily` | cleanup → epsilon → kmeans → calculate 4단계 전체 순차 실행 |
| `POST` | `/internal/batch/receive` | OpenAI Batch API 결과 수령 (02:00 스케줄러와 동일) |

epsilon·kmeans·calculate 엔드포인트는 body에 UUID 목록을 전달하며, 비어 있으면 어제 활성 유저를 자동 조회해 실행합니다.

---

## 문서

| 문서 | 내용 |
|------|------|
| [docs/architecture.md](docs/architecture.md) | 서비스 아키텍처, 데이터베이스 스키마, Kafka 이벤트 처리 |
| [docs/recommendation-algorithm.md](docs/recommendation-algorithm.md) | 추천 알고리즘 상세 (K-Means, epsilon-greedy, LLM Re-ranking) |
| [docs/embedding-pipeline.md](docs/embedding-pipeline.md) | 임베딩 파이프라인 설계 및 모델 선택 근거 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 개발 중 발생한 버그 및 해결 과정 |
