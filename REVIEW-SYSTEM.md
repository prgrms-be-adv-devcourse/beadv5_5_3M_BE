# Gemini AI PR Code Review System

PR이 열리면 GitHub Actions가 자동으로 변경된 서비스를 감지하고, Gemini API를 호출하여 **CRITICAL 심각도 이슈만** PR 코멘트로 게시합니다.

---

## 최초 설정 (1회만)

Repository **Settings → Secrets and variables → Actions** 에서 Secret 추가:

| Secret 이름 | 값 |
|-------------|-----|
| `GEMINI_API_KEY` | Google AI Studio ([aistudio.google.com](https://aistudio.google.com)) 에서 발급한 API 키 |

설정 후 PR을 열면 자동으로 동작합니다.

---

## 동작 흐름

```
PR 오픈 / 푸시
    │
    ▼
[detect-changes] ──────────────── 변경된 *-service/ 자동 감지
    │
    ├──────────────────────────────────────────┐
    ▼                                          ▼
[build-test] (matrix)                  [gemini-review]
 서비스별 ./gradlew build               서비스별 Gemini API 호출
 빌드 실패 시 artifact 업로드           → PR 코멘트 게시/업데이트
```

- **빌드와 Gemini 리뷰는 병렬**로 실행됩니다. 빌드가 실패해도 리뷰는 독립적으로 수행됩니다.
- PR에 새 커밋을 푸시하면 **기존 리뷰 코멘트를 수정**합니다 (새 코멘트를 추가하지 않습니다).

---

## 트리거 조건

| 이벤트 | 대상 브랜치 |
|--------|-------------|
| PR 오픈 / 업데이트 / 재오픈 | `main`, `develop`, `dev/*` |

서비스 디렉토리(`*-service/`) 외의 파일만 변경된 경우 리뷰가 스킵됩니다.
단, `.github/review-rules/_common.yml` 변경 시에는 전체 서비스가 리뷰 대상이 됩니다.

---

## PR 코멘트 읽는 법

```
## 🤖 Gemini Code Review

### 📦 `ticket-service`
> 예약 흐름에 동시성 보호가 누락됨.

| 심각도 | 카테고리 | 파일 | 발견 사항 |
|--------|----------|------|-----------|
| 🔴 CRITICAL | concurrency | `TicketRepositoryImpl.java:38` | 비관적 락 없이 티켓 조회 ... |
```

**CRITICAL만 표시됩니다.** 발견 사항이 없으면 `✅ 발견된 문제 없음`으로 표시됩니다.

카테고리 의미:

| 카테고리 | 설명 |
|----------|------|
| `concurrency` | 레이스 컨디션, 락 누락, 초과 예약 |
| `security` | 인증 우회, 시크릿 노출, 헤더 주입 |
| `integrity` | 금융 무결성, 이중 청구, 멱등성 누락 |
| `reliability` | 이벤트 유실, 재시도 불가, 트랜잭션 경계 위반 |
| `layer-violation` | 헥사고날 아키텍처 레이어 경계 위반 |

---

## 규칙 추가하기

### 기존 서비스에 규칙 추가

`.github/review-rules/{서비스명}.yml`을 편집합니다:

```yaml
rules:
  - id: TKT-002          # 서비스 접두어 + 번호
    severity: CRITICAL   # CRITICAL만 추가 (다른 심각도는 무시됨)
    title: 규칙 제목 (한 줄)
    description: >
      언제 이 문제가 발생하는지, 왜 위험한지 설명합니다.
      여러 줄로 작성 가능합니다.
```

### 새 서비스 추가

1. `{서비스명}-service/` 디렉토리에 `build.gradle` 파일이 있으면 **자동으로 감지**됩니다.
2. `.github/review-rules/{서비스명}-service.yml` 파일을 생성하면 해당 서비스 전용 규칙이 적용됩니다 (없어도 공통 규칙은 적용됨).

### 공통/기술스택 규칙

| 파일 | 적용 조건 |
|------|-----------|
| `_common.yml` | 모든 서비스 항상 적용 |
| `_java-spring.yml` | diff에 `.java` 파일 포함 시 |
| `_kafka.yml` | diff에 `kafka`, `messaging`, `consumer`, `producer` 경로 포함 시 |

---

## 트러블슈팅

### 리뷰 코멘트가 생성되지 않는 경우

1. `GEMINI_API_KEY` Secret이 등록되었는지 확인
2. Actions 탭 → 해당 워크플로우 실행 → `Gemini Code Review` job 로그 확인
3. 변경 파일이 `*-service/` 디렉토리 안에 있는지 확인

### 빌드는 실패했지만 리뷰는 원하는 경우

정상 동작입니다. 빌드와 리뷰는 독립적으로 실행되므로 빌드 실패와 무관하게 Gemini 리뷰는 항상 수행됩니다.

### "토큰 예산 초과로 리뷰를 건너뜁니다" 메시지

PR에서 너무 많은 서비스가 동시에 변경되었습니다. 변경이 감지된 순서대로 처리되며, 예산 초과 시 이후 서비스는 스킵됩니다. 서비스를 분리하여 PR을 나누는 것을 권장합니다.

### 리뷰 결과가 비어있는 경우 (`findings: []`)

CRITICAL 이슈가 없다는 의미입니다. 정상입니다.

---

## 파일 구조

```
.github/
├── workflows/
│   ├── pr-review.yml              # 메인 워크플로우
│   └── build-test-service.yml     # 재사용 빌드 워크플로우
├── review-rules/
│   ├── _common.yml                # 공통 규칙 (항상 적용)
│   ├── _java-spring.yml           # Java/Spring 규칙
│   ├── _kafka.yml                 # Kafka 규칙
│   └── {service}.yml              # 서비스별 도메인 규칙
├── scripts/
│   ├── detect-changed-services.sh # 변경 서비스 감지
│   ├── build-gemini-prompt.sh     # 프롬프트 조립
│   ├── call-gemini-api.sh         # Gemini API 호출
│   └── post-review-comment.sh     # PR 코멘트 게시
└── prompts/
    ├── system-prompt.md           # Gemini 시스템 프롬프트
    └── output-schema.json         # 응답 JSON 스키마
```
