# 임베딩 파이프라인

## 목차

- [파이프라인 흐름](#파이프라인-흐름)
- [모델 선택](#모델-선택)
- [설계 근거](#설계-근거)
- [Spring AI를 쓰지 않는 이유](#spring-ai를-쓰지-않는-이유)
- [코드 구조](#코드-구조)

---

## 파이프라인 흐름

`movie.ai.created` 이벤트 수신 시, 또는 `movie.ai.updated`에서 category·description 변경 시 실행됩니다.

```
한국어 description + 원본 category[]
        ↓
   Step 1. LLM 호출 (gpt-4o-mini, 1회)
   → 영어 summary + 영어 카테고리 번역을 JSON으로 반환
   → response_format: json_object (마크다운 코드블록 등 노이즈 없이 파싱)
        ↓
   Step 2. 임베딩 호출 (text-embedding-3-small)
   → "[Action, Crime] " + summary 형태로 조합 후 전달
   → vector(1536) 반환
        ↓
   movies_embedded 저장:
     embedding   = vector(1536)
     summary     = 영어 요약
     category    = ["Action", "Crime"]  (LLM 번역 결과)
```

---

## 모델 선택

| 역할 | 모델 | 선택 이유 |
|------|------|-----------|
| 요약·번역 | `gpt-4o-mini` | 구조화 JSON 출력 안정적, Batch API 공식 지원, 비용 저렴 |
| LLM Re-ranking | `gpt-4o-mini` | 동일 이유. Re-ranking은 복잡한 추론 불필요 |
| 임베딩 | `text-embedding-3-small` | 영어 성능 우수, 1536차원, 비용 효율적 |

### Re-ranking 모델 비용 비교 (1,000명 기준 / 일)

| 모델 | Input ($/1M) | Output ($/1M) | 일 비용 | 특이사항 |
|------|-------------|--------------|---------|---------|
| gpt-5.1 | $1.25 | $10.00 | ~$2.00 | 구조화 랭킹에는 과한 성능 |
| gpt-5-mini | $0.25 | $2.00 | ~$0.40 | Batch API 할인 미확인 |
| **gpt-4o-mini** | **$0.15** | **$0.60** | **~$0.29** | Batch API 공식 지원 |

> 비용 기준: 유저당 입력 ~1,500 tokens (후보 70개 × 약 20 tokens), 출력 ~50 tokens

---

## 설계 근거

### 요약 먼저 → 임베딩

description 전문을 직접 임베딩하지 않고, LLM 요약 후 임베딩합니다.
- 노이즈 제거: 홍보성 문구, 배우 이름 등 추천과 무관한 정보 제거
- 토큰 절약: 긴 한국어 description → 짧은 영어 summary
- 핵심 의미 집중: 줄거리와 장르 핵심만 남음

### 카테고리 prefix 포함

```
"[Action, Crime] A detective hunts a serial killer..."
```

줄거리가 비슷해도 장르가 다른 영화(예: 액션 스릴러 vs 로맨스 드라마)가 ANN 검색에서 뒤섞이는 것을 방지합니다. RAG 메타데이터 prefix 패턴에서 검증된 기법입니다.

### 영어 요약 사용

`text-embedding-3-small`은 영어 성능이 한국어보다 높습니다. 번역과 요약을 1회 LLM 호출로 통합해 비용을 절감합니다.

### movies_embedded.category 비정규화 저장

LLM Re-ranking 쿼리가 `movies_embedded` 단독으로 완결되도록 비정규화합니다. movie 테이블 JOIN이 불필요합니다.

| 컬럼 | 저장 값 | 용도 |
|------|---------|------|
| `movie.category` | 사용자 입력 원본 (한국어/영어 혼용) | 조회 전용 |
| `movies_embedded.category` | LLM 영어 번역값 | 임베딩 prefix + Re-ranking 프롬프트 |

### dimensions 파라미터 전달 흐름

```
application-dev.yaml
  openai.embedding-dimensions: 1536
        ↓ @Value 주입
  embeddingDimensions = 1536
        ↓ HTTP body에 포함
  POST /v1/embeddings
  Body: { "model": "text-embedding-3-small", "input": "...", "dimensions": 1536 }
        ↓
  OpenAI → 1536차원 벡터 반환
```

`dimensions=1536`을 명시하는 이유: 향후 `text-embedding-3-large`로 교체 시 이 파라미터로 출력 차원을 1536으로 제한해 스키마 변경 없이 교체 가능합니다.

---

## Spring AI를 쓰지 않는 이유

Spring AI를 사용하면 코드가 짧아지지만, 이 서비스의 커스텀 요구사항을 충족하기 어렵습니다.

**Spring AI 사용 시**:
```java
String content = chatClient.prompt().user(prompt).call().content();
float[] vector = embeddingModel.embed(text);
```

**현재 (직접 RestClient)**:
```java
Map<String, Object> requestBody = Map.of(
    "model", summaryModel,
    "response_format", Map.of("type", "json_object"),
    "messages", List.of(Map.of("role", "user", "content", prompt))
);
// ... RestClient 호출
```

**직접 구현을 선택한 이유**:

1. **`dimensions` 파라미터**: Spring AI `EmbeddingModel`의 파라미터 노출 여부 불확실
2. **`response_format: json_object`**: Spring AI structured output은 내부 동작이 달라 추가 설정 필요
3. **커스텀 파이프라인**: LLM 요약 → 임베딩을 하나의 메서드에서 순서대로 처리하는 구조가 Spring AI 패턴과 불일치
4. **교체 가능성 확보**: `EmbeddingClient` 인터페이스로 추상화되어 있어 구현체 교체 가능

---

## 코드 구조

```
infrastructure/embedding/
├── EmbeddingClient.java          ← 인터페이스 (포트)
├── EmbeddingResult.java          ← 결과 DTO (embedding, summary, categoriesEn)
└── OpenAiEmbeddingClient.java    ← 실제 구현체 (어댑터)

infrastructure/llm/
├── LlmRerankingClient.java       ← 인터페이스 (포트)
└── OpenAiLlmRerankingClient.java ← 구현체 (Batch API + 즉시 호출)
```

`embed(description, category)` 한 번 호출로 LLM 요약과 임베딩이 순서대로 실행됩니다.

**`OpenAiLlmRerankingClient` timeout 설정**:
- `rerankImmediate()`: 연결·읽기 각 5초 제한 (`immediateRestClient`)
- Batch API 제출/수령: timeout 없음 (장시간 대기가 정상)
