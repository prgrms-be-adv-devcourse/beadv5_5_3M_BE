#!/usr/bin/env bash
# detect-changed-services.sh
# 변경된 *-service/ 디렉토리를 자동 감지하여 JSON 배열로 출력합니다.
#
# Usage:
#   ./detect-changed-services.sh <base_sha> <head_sha>
#
# Output (stdout):
#   JSON array  →  ["ticket-service","user-service"]
#
# Special cases:
#   - .github/review-rules/_common.yml 변경 시 모든 서비스 포함
#
# Exclusions (리뷰 대상 아님):
#   - 마크다운/텍스트 문서: *.md, *.txt
#   - 메타 파일: .gitignore, .gitattributes, LICENSE
#   - 한 서비스에서 위 파일들만 바뀌면 해당 서비스는 리뷰 스킵

set -euo pipefail

BASE_SHA="${1:?base_sha is required}"
HEAD_SHA="${2:?head_sha is required}"

REPO_ROOT="$(git rev-parse --show-toplevel)"

# 리뷰 대상이 아닌 파일 패턴 (정규식, 전체 경로 기준)
EXCLUDE_PATTERN='\.(md|txt)$|(^|/)(\.gitignore|\.gitattributes|LICENSE)$'

# 변경된 파일 목록 (리뷰 제외 대상 필터링)
RAW_CHANGED=$(git diff --name-only "$BASE_SHA" "$HEAD_SHA" 2>/dev/null || git diff --name-only HEAD~1 HEAD)
CHANGED_FILES=$(echo "$RAW_CHANGED" | grep -vE "$EXCLUDE_PATTERN" || true)

# 전체 서비스 강제 포함 조건 체크
FORCE_ALL=false
if echo "$CHANGED_FILES" | grep -qE '^\.github/review-rules/_common\.yml$'; then
  echo "::notice::_common.yml 변경 감지 → 전체 서비스 리뷰 적용" >&2
  FORCE_ALL=true
fi

# 레포지토리 내 모든 *-service 디렉토리 목록
ALL_SERVICES=()
while IFS= read -r svc_dir; do
  svc_name=$(basename "$svc_dir")
  if [[ -f "$REPO_ROOT/$svc_name/build.gradle" || -f "$REPO_ROOT/$svc_name/build.gradle.kts" ]]; then
    ALL_SERVICES+=("$svc_name")
  fi
done < <(find "$REPO_ROOT" -maxdepth 1 -type d -name '*-service' | sort)

if $FORCE_ALL; then
  # 전체 서비스 JSON 배열 출력
  printf '%s\n' "${ALL_SERVICES[@]}" | jq -R . | jq -sc .
  exit 0
fi

# 변경된 파일에서 서비스 디렉토리 추출
declare -A SEEN
CHANGED_SERVICES=()

while IFS= read -r file; do
  # 첫 번째 경로 컴포넌트 추출 (예: "ticket-service/src/..." → "ticket-service")
  svc=$(echo "$file" | cut -d'/' -f1)

  # *-service 패턴 매칭 + build.gradle 존재 확인 + 중복 제거
  if [[ "$svc" == *-service ]] && \
     [[ -z "${SEEN[$svc]+x}" ]] && \
     ([[ -f "$REPO_ROOT/$svc/build.gradle" ]] || [[ -f "$REPO_ROOT/$svc/build.gradle.kts" ]]); then
    SEEN[$svc]=1
    CHANGED_SERVICES+=("$svc")
  fi
done <<< "$CHANGED_FILES"

if [[ ${#CHANGED_SERVICES[@]} -eq 0 ]]; then
  echo "[]"
else
  printf '%s\n' "${CHANGED_SERVICES[@]}" | jq -R . | jq -sc .
fi
