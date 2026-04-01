#!/usr/bin/env bash
# detect-changed-services-cicd.sh
# CI/CD 전용: *-service/ 디렉토리 변경만 감지합니다.
# CLAUDE.md, _common.yml 등 비서비스 파일 변경은 전체 트리거 없이 무시합니다.
#
# Usage:
#   ./detect-changed-services-cicd.sh <base_sha> <head_sha>
#
# Output (stdout):
#   JSON array  →  ["ticket-service","user-service"]

set -euo pipefail

BASE_SHA="${1:?base_sha is required}"
HEAD_SHA="${2:?head_sha is required}"

REPO_ROOT="$(git rev-parse --show-toplevel)"

# 변경된 파일 목록
# Zero SHA (0000...): 최초 push 또는 force push 시 github.event.before 값
# → git diff 불가능하므로 HEAD 커밋 단위로 변경 파일 목록을 가져옴
if [[ "$BASE_SHA" =~ ^0+$ ]]; then
  CHANGED_FILES=$(git diff-tree --no-commit-id -r --name-only HEAD 2>/dev/null || echo "")
else
  CHANGED_FILES=$(git diff --name-only "$BASE_SHA" "$HEAD_SHA" 2>/dev/null || git diff --name-only HEAD~1 HEAD)
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
