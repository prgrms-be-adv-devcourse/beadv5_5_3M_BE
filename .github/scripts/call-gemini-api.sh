#!/usr/bin/env bash
# call-gemini-api.sh
# Gemini API를 호출하여 코드 리뷰 결과를 JSON으로 반환합니다.
#
# Usage:
#   ./call-gemini-api.sh <prompt_file> <output_file>
#
# Environment variables:
#   GEMINI_API_KEY      (required) Google AI Studio API key
#   GEMINI_MODEL        (optional) default: gemini-2.0-flash
#   MAX_OUTPUT_TOKENS   (optional) default: 4096
#
# Output:
#   Writes Gemini JSON response to <output_file>
#   Exits 0 on success, non-zero on failure

set -euo pipefail

PROMPT_FILE="${1:?prompt_file is required}"
OUTPUT_FILE="${2:?output_file is required}"

GEMINI_API_KEY="${GEMINI_API_KEY:?GEMINI_API_KEY environment variable is required}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.0-flash}"
MAX_OUTPUT_TOKENS="${MAX_OUTPUT_TOKENS:-4096}"
MAX_RETRIES=3
RETRY_DELAY=1

# system-prompt.md와 output-schema.json 경로
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"
SYSTEM_PROMPT_FILE="$REPO_ROOT/.github/prompts/system-prompt.md"
OUTPUT_SCHEMA_FILE="$REPO_ROOT/.github/prompts/output-schema.json"

if [[ ! -f "$SYSTEM_PROMPT_FILE" ]]; then
  echo "::error::system-prompt.md not found at $SYSTEM_PROMPT_FILE" >&2
  exit 1
fi

if [[ ! -f "$PROMPT_FILE" ]]; then
  echo "::error::Prompt file not found: $PROMPT_FILE" >&2
  exit 1
fi

# Gemini API 요청 JSON 조립
# --rawfile: 파일 내용을 문자열로 읽음 (--arg와 달리 셸 인자를 거치지 않아 ARG_MAX 제한 없음)
# --slurpfile: 파일 내용을 JSON 배열로 파싱 ($output_schema[0]으로 접근)
jq -n \
  --rawfile system_prompt "$SYSTEM_PROMPT_FILE" \
  --rawfile user_prompt "$PROMPT_FILE" \
  --slurpfile output_schema "$OUTPUT_SCHEMA_FILE" \
  --argjson max_tokens "$MAX_OUTPUT_TOKENS" \
  '{
    "system_instruction": {
      "parts": [{"text": $system_prompt}]
    },
    "contents": [
      {
        "role": "user",
        "parts": [{"text": $user_prompt}]
      }
    ],
    "generationConfig": {
      "temperature": 0.2,
      "maxOutputTokens": $max_tokens,
      "responseMimeType": "application/json",
      "responseSchema": $output_schema[0]
    }
  }' > /tmp/gemini_request.json

API_URL="https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}"

# 재시도 로직
attempt=1
while [[ $attempt -le $MAX_RETRIES ]]; do
  echo "::debug::Gemini API 호출 시도 $attempt/$MAX_RETRIES (model: $GEMINI_MODEL)" >&2

  HTTP_CODE=$(curl -s -w "%{http_code}" \
    -o /tmp/gemini_raw_response.json \
    -D /tmp/gemini_headers.txt \
    -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d @/tmp/gemini_request.json \
    --max-time 120)

  if [[ "$HTTP_CODE" == "200" ]]; then
    # 응답에서 실제 텍스트 콘텐츠 추출
    CONTENT=$(jq -r '.candidates[0].content.parts[0].text // empty' /tmp/gemini_raw_response.json 2>/dev/null)

    if [[ -z "$CONTENT" ]]; then
      echo "::error::Gemini 응답에서 content를 추출할 수 없습니다." >&2
      cat /tmp/gemini_raw_response.json >&2
      exit 1
    fi

    # 토큰 사용량 및 종료 이유 로깅
    INPUT_TOKENS=$(jq -r '.usageMetadata.promptTokenCount // 0' /tmp/gemini_raw_response.json)
    OUTPUT_TOKENS=$(jq -r '.usageMetadata.candidatesTokenCount // 0' /tmp/gemini_raw_response.json)
    FINISH_REASON=$(jq -r '.candidates[0].finishReason // "unknown"' /tmp/gemini_raw_response.json)
    echo "::notice::토큰 사용량 — 입력: ${INPUT_TOKENS}, 출력: ${OUTPUT_TOKENS}, 종료 이유: ${FINISH_REASON}" >&2

    # GitHub Actions step summary에 토큰 사용량 기록
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      echo "| $GEMINI_MODEL | $INPUT_TOKENS | $OUTPUT_TOKENS |" >> "$GITHUB_STEP_SUMMARY"
    fi

    # JSON 유효성 검증 후 출력
    if echo "$CONTENT" | jq . > /dev/null 2>&1; then
      echo "$CONTENT" > "$OUTPUT_FILE"
      echo "::notice::Gemini 리뷰 완료 — 결과 저장: $OUTPUT_FILE" >&2
      exit 0
    else
      echo "::error::Gemini 응답이 유효한 JSON이 아닙니다." >&2
      echo "$CONTENT" >&2
      exit 1
    fi

  elif [[ "$HTTP_CODE" == "429" ]]; then
    # Retry-After 헤더 우선, 없으면 지수 백오프
    RETRY_AFTER=$(grep -i '^retry-after:' /tmp/gemini_headers.txt 2>/dev/null | awk '{print $2}' | tr -d '\r' || true)
    if [[ -n "$RETRY_AFTER" && "$RETRY_AFTER" =~ ^[0-9]+$ ]]; then
      WAIT_TIME="$RETRY_AFTER"
    else
      WAIT_TIME=$((RETRY_DELAY * (2 ** (attempt - 1))))
    fi
    echo "::warning::429 Too Many Requests — ${WAIT_TIME}초 후 재시도 ($attempt/$MAX_RETRIES)" >&2
    sleep "$WAIT_TIME"
    attempt=$((attempt + 1))

  else
    echo "::error::Gemini API 오류 (HTTP $HTTP_CODE)" >&2
    cat /tmp/gemini_raw_response.json >&2
    exit 1
  fi
done

echo "::error::Gemini API 최대 재시도 횟수($MAX_RETRIES) 초과" >&2
exit 1
