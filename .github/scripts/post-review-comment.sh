#!/usr/bin/env bash
# post-review-comment.sh
# Gemini 리뷰 JSON 결과들을 마크다운 PR 코멘트로 변환하여 게시합니다.
#
# Usage:
#   ./post-review-comment.sh <pr_number> <review_results_dir>
#
# Environment variables:
#   GITHUB_TOKEN   (required) GitHub token for gh CLI
#   GITHUB_REPOSITORY  (required, auto-set in Actions) owner/repo
#
# review_results_dir: 디렉토리 내 각 *.json 파일이 서비스 하나의 리뷰 결과
# 마커(<!-- gemini-review-id -->)로 기존 코멘트를 찾아 업데이트하고,
# 없으면 새 코멘트를 생성합니다.

set -euo pipefail

PR_NUMBER="${1:?pr_number is required}"
RESULTS_DIR="${2:?review_results_dir is required}"

MARKER="<!-- gemini-review-id -->"
TEMP_COMMENT="/tmp/gemini_review_comment.md"

severity_icon() {
  case "$1" in
    CRITICAL) echo "🔴" ;;
    HIGH)     echo "🟠" ;;
    MEDIUM)   echo "🟡" ;;
    LOW)      echo "🔵" ;;
    *)        echo "⚪" ;;
  esac
}

# 마크다운 코멘트 생성
{
  echo "$MARKER"
  echo "## 🤖 Gemini Code Review"
  echo ""
  echo "> 자동 코드 리뷰 — 참고용입니다. 머지를 차단하지 않습니다."
  echo ""

  TOTAL_CRITICAL=0
  TOTAL_HIGH=0
  TOTAL_MEDIUM=0
  TOTAL_LOW=0

  # 각 서비스 리뷰 결과 처리
  for result_file in "$RESULTS_DIR"/*.json; do
    [[ -f "$result_file" ]] || continue

    SERVICE=$(jq -r '.service // "unknown"' "$result_file")
    SUMMARY=$(jq -r '.summary // ""' "$result_file")
    FINDING_COUNT=$(jq '.findings | length' "$result_file")
    PRAISE_COUNT=$(jq '.praise | length' "$result_file")

    echo "---"
    echo ""
    echo "### 📦 \`$SERVICE\`"
    if [[ -n "$SUMMARY" ]]; then
      echo "> $SUMMARY"
    fi
    echo ""

    if [[ "$FINDING_COUNT" -eq 0 ]]; then
      echo "✅ 발견된 문제 없음"
    else
      echo "| 심각도 | 카테고리 | 파일 | 발견 사항 |"
      echo "|--------|----------|------|-----------|"

      while IFS= read -r finding; do
        SEVERITY=$(echo "$finding" | jq -r '.severity')
        CATEGORY=$(echo "$finding" | jq -r '.category')
        FILE=$(echo "$finding" | jq -r '.file')
        LINE=$(echo "$finding" | jq -r '.line_range')
        TITLE=$(echo "$finding" | jq -r '.title')
        DESC=$(echo "$finding" | jq -r '.description')
        SUGG=$(echo "$finding" | jq -r '.suggestion // empty')
        ICON=$(severity_icon "$SEVERITY")

        echo "| $ICON $SEVERITY | \`$CATEGORY\` | \`$FILE:$LINE\` | **$TITLE**<br>$DESC$([ -n "$SUGG" ] && echo "<br>💡 $SUGG") |"

        case "$SEVERITY" in
          CRITICAL) TOTAL_CRITICAL=$((TOTAL_CRITICAL + 1)) ;;
          HIGH)     TOTAL_HIGH=$((TOTAL_HIGH + 1)) ;;
          MEDIUM)   TOTAL_MEDIUM=$((TOTAL_MEDIUM + 1)) ;;
          LOW)      TOTAL_LOW=$((TOTAL_LOW + 1)) ;;
        esac
      done < <(jq -c '.findings[]' "$result_file")
    fi

    # 칭찬 항목
    if [[ "$PRAISE_COUNT" -gt 0 ]]; then
      echo ""
      echo "**✅ 잘된 점:**"
      while IFS= read -r praise; do
        echo "- $praise"
      done < <(jq -r '.praise[]' "$result_file")
    fi

    echo ""
  done

  # 요약 섹션
  echo "---"
  echo ""
  echo "### 📊 전체 요약"
  echo ""
  echo "| 🔴 CRITICAL | 🟠 HIGH | 🟡 MEDIUM | 🔵 LOW |"
  echo "|------------|--------|----------|-------|"
  echo "| $TOTAL_CRITICAL | $TOTAL_HIGH | $TOTAL_MEDIUM | $TOTAL_LOW |"
  echo ""

  # 메타데이터
  RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID:-0}"
  echo "<details><summary>리뷰 메타데이터</summary>"
  echo ""
  echo "- **실행 시각:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
  echo "- **모델:** \`${GEMINI_MODEL:-gemini-3-flash-preview}\`"
  echo "- **워크플로우:** [Actions 실행 결과]($RUN_URL)"
  echo ""
  echo "| 모델 | 입력 토큰 | 출력 토큰 |"
  echo "|------|----------|----------|"
  # 토큰 사용량은 call-gemini-api.sh에서 GITHUB_STEP_SUMMARY에 기록됨
  echo "| (workflow summary 참조) | — | — |"
  echo ""
  echo "</details>"

} > "$TEMP_COMMENT"

# 코멘트 길이 체크 (GitHub 65536자 제한)
COMMENT_LENGTH=$(wc -c < "$TEMP_COMMENT")
if [[ "$COMMENT_LENGTH" -gt 60000 ]]; then
  echo "::warning::코멘트가 너무 깁니다 (${COMMENT_LENGTH}자). <details> 래핑으로 압축합니다." >&2

  # 서비스별 finding 테이블을 <details>로 감싸서 재생성
  {
    echo "$MARKER"
    echo "## 🤖 Gemini Code Review"
    echo ""
    echo "> 자동 코드 리뷰 — 참고용입니다. 머지를 차단하지 않습니다."
    echo ""

    TOTAL_CRITICAL=0; TOTAL_HIGH=0; TOTAL_MEDIUM=0; TOTAL_LOW=0

    for result_file in "$RESULTS_DIR"/*.json; do
      [[ -f "$result_file" ]] || continue
      SERVICE=$(jq -r '.service // "unknown"' "$result_file")
      SUMMARY=$(jq -r '.summary // ""' "$result_file")
      FINDING_COUNT=$(jq '.findings | length' "$result_file")
      SVC_CRITICAL=$(jq '[.findings[] | select(.severity=="CRITICAL")] | length' "$result_file")
      SVC_HIGH=$(jq '[.findings[] | select(.severity=="HIGH")] | length' "$result_file")
      TOTAL_CRITICAL=$((TOTAL_CRITICAL + SVC_CRITICAL))
      TOTAL_HIGH=$((TOTAL_HIGH + SVC_HIGH))
      TOTAL_MEDIUM=$((TOTAL_MEDIUM + $(jq '[.findings[] | select(.severity=="MEDIUM")] | length' "$result_file")))
      TOTAL_LOW=$((TOTAL_LOW + $(jq '[.findings[] | select(.severity=="LOW")] | length' "$result_file")))

      echo "---"
      echo ""
      BADGE=""
      [[ $SVC_CRITICAL -gt 0 ]] && BADGE=" 🔴×${SVC_CRITICAL}"
      [[ $SVC_HIGH -gt 0 ]] && BADGE="${BADGE} 🟠×${SVC_HIGH}"
      echo "<details><summary>📦 <code>$SERVICE</code>${BADGE} — $SUMMARY</summary>"
      echo ""
      if [[ "$FINDING_COUNT" -eq 0 ]]; then
        echo "✅ 발견된 문제 없음"
      else
        echo "| 심각도 | 카테고리 | 파일 | 발견 사항 |"
        echo "|--------|----------|------|-----------|"
        while IFS= read -r finding; do
          SEVERITY=$(echo "$finding" | jq -r '.severity')
          CATEGORY=$(echo "$finding" | jq -r '.category')
          FILE=$(echo "$finding" | jq -r '.file')
          LINE=$(echo "$finding" | jq -r '.line_range')
          TITLE=$(echo "$finding" | jq -r '.title')
          DESC=$(echo "$finding" | jq -r '.description')
          SUGG=$(echo "$finding" | jq -r '.suggestion // empty')
          ICON=$(severity_icon "$SEVERITY")
          echo "| $ICON $SEVERITY | \`$CATEGORY\` | \`$FILE:$LINE\` | **$TITLE**<br>$DESC$([ -n "$SUGG" ] && echo "<br>💡 $SUGG") |"
        done < <(jq -c '.findings[]' "$result_file")
      fi
      echo ""
      echo "</details>"
      echo ""
    done

    echo "---"
    echo ""
    echo "### 📊 전체 요약"
    echo ""
    echo "| 🔴 CRITICAL | 🟠 HIGH | 🟡 MEDIUM | 🔵 LOW |"
    echo "|------------|--------|----------|-------|"
    echo "| $TOTAL_CRITICAL | $TOTAL_HIGH | $TOTAL_MEDIUM | $TOTAL_LOW |"
    echo ""
    RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID:-0}"
    echo "<details><summary>리뷰 메타데이터</summary>"
    echo ""
    echo "- **실행 시각:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo "- **모델:** \`${GEMINI_MODEL:-gemini-3-flash-preview}\`"
    echo "- **워크플로우:** [Actions 실행 결과]($RUN_URL)"
    echo ""
    echo "</details>"
  } > "$TEMP_COMMENT"
fi

# 기존 Gemini 리뷰 코멘트 찾기 (마커 기반)
EXISTING_COMMENT_ID=$(gh api \
  "repos/${GITHUB_REPOSITORY}/issues/${PR_NUMBER}/comments" \
  --jq ".[] | select(.body | contains(\"$MARKER\")) | .id" \
  2>/dev/null | head -1 || true)

if [[ -n "$EXISTING_COMMENT_ID" ]]; then
  echo "::notice::기존 Gemini 리뷰 코멘트 업데이트 (ID: $EXISTING_COMMENT_ID)" >&2
  gh api \
    --method PATCH \
    "repos/${GITHUB_REPOSITORY}/issues/comments/${EXISTING_COMMENT_ID}" \
    --field body="$(cat "$TEMP_COMMENT")" \
    > /dev/null
else
  echo "::notice::새 Gemini 리뷰 코멘트 생성" >&2
  gh pr comment "$PR_NUMBER" \
    --body-file "$TEMP_COMMENT"
fi

echo "::notice::PR #$PR_NUMBER 코멘트 게시 완료" >&2
