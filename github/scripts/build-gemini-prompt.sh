#!/usr/bin/env bash
# build-gemini-prompt.sh
# 서비스별 리뷰 규칙 + CLAUDE.md 컨텍스트 + diff를 Gemini 친화적인 구조로 조립합니다.
#
# Usage:
#   ./build-gemini-prompt.sh <service_name> <diff_file> <output_prompt_file>
#
# 규칙 적용 계층:
#   [Common]  .github/review-rules/_common.yml         → 항상 포함
#   [Tech]    .github/review-rules/_java-spring.yml    → *.java 변경 시
#   [Infra]   .github/review-rules/_kafka.yml          → kafka/messaging/consumer/producer 경로 포함 시
#   [Domain]  .github/review-rules/{service}.yml       → 해당 서비스 변경 시

set -euo pipefail

SERVICE="${1:?service_name is required}"
DIFF_FILE="${2:?diff_file is required}"
OUTPUT_FILE="${3:?output_prompt_file is required}"

REPO_ROOT="$(git rev-parse --show-toplevel)"
RULES_DIR="$REPO_ROOT/.github/review-rules"

# YAML 규칙 파일에서 title + description만 추출하여 자연어 목록으로 변환
# (yq 없이 순수 bash — YAML 포맷 의존: "  title:" / "  description: >")
extract_rules_as_text() {
  local file="$1"
  [[ -f "$file" ]] || return 0

  local in_desc=false
  local current_title=""
  local current_desc=""

  while IFS= read -r line; do
    # title 라인
    if [[ "$line" =~ ^[[:space:]]+title:[[:space:]]*(.*) ]]; then
      # 이전 항목 출력
      if [[ -n "$current_title" ]]; then
        echo "- ${current_title}: ${current_desc}" | tr -s ' '
      fi
      current_title="${BASH_REMATCH[1]}"
      current_desc=""
      in_desc=false

    # description 시작
    elif [[ "$line" =~ ^[[:space:]]+description:[[:space:]]*(.*) ]]; then
      in_desc=true
      local val="${BASH_REMATCH[1]}"
      # "> " 멀티라인 마커 제거
      val="${val#>}"
      val="${val# }"
      current_desc="$val"

    # 멀티라인 description 계속 (들여쓰기 6칸+)
    elif $in_desc && [[ "$line" =~ ^[[:space:]]{6,}(.*) ]]; then
      local continuation="${BASH_REMATCH[1]}"
      [[ -n "$continuation" ]] && current_desc="${current_desc} ${continuation}"

    # 다른 최상위 필드가 나오면 description 종료
    elif [[ "$line" =~ ^[[:space:]]{2,4}[a-z_]+: ]]; then
      in_desc=false
    fi
  done < "$file"

  # 마지막 항목 출력
  if [[ -n "$current_title" ]]; then
    echo "- ${current_title}: ${current_desc}" | tr -s ' '
  fi
}


{
  # ── 1. 리뷰 대상 명시
  echo "<review_request>"
  echo "You are reviewing a pull request for the service: ${SERVICE}"
  echo "Focus ONLY on CRITICAL issues — data corruption, race conditions, security vulnerabilities, financial integrity."
  echo "Do NOT report HIGH, MEDIUM, or LOW severity findings."
  echo "If no CRITICAL issues exist, return findings as an empty array []."
  echo "</review_request>"
  echo ""

  # ── 2. 규칙 (YAML에서 title+description만 추출)
  echo "<critical_check_rules>"
  echo "Check the diff against these CRITICAL-level rules only:"
  echo ""

  extract_rules_as_text "$RULES_DIR/_common.yml"

  if grep -q '\.java' "$DIFF_FILE" 2>/dev/null; then
    extract_rules_as_text "$RULES_DIR/_java-spring.yml"
  fi

  if grep -qiE 'kafka|messaging|consumer|producer' "$DIFF_FILE" 2>/dev/null; then
    extract_rules_as_text "$RULES_DIR/_kafka.yml"
  fi

  SERVICE_RULES="$RULES_DIR/${SERVICE}.yml"
  if [[ -f "$SERVICE_RULES" ]]; then
    extract_rules_as_text "$SERVICE_RULES"
  fi

  echo "</critical_check_rules>"
  echo ""

  # ── 3. Diff (변경 코드만)
  echo "<git_diff service=\"${SERVICE}\">"
  echo "Review ONLY the following code changes. Do not comment on code not shown here."
  echo ""
  cat "$DIFF_FILE"
  echo "</git_diff>"
  echo ""
  echo "Return your review as a JSON object matching the schema in your instructions."

} > "$OUTPUT_FILE"

echo "::debug::프롬프트 생성 완료: $OUTPUT_FILE ($(wc -c < "$OUTPUT_FILE") bytes)" >&2
