#!/usr/bin/env bash
# K3s 클러스터 dev 네임스페이스에 9개 서비스용 Secret 일괄 생성.
#
# 사용법:
#   1. k8s/infra/.env.k3s.example 을 k8s/infra/.env.k3s 로 복사
#   2. k8s/infra/.env.k3s 안의 값을 실제 값으로 채우기 (git 에 절대 commit X — .gitignore 로 차단됨)
#   3. ./k8s/infra/03-secrets.sh
#
# 이 스크립트는 idempotent 함: 같은 이름의 Secret 이 이미 있어도 apply 로 덮어씀.

set -euo pipefail

ENV_FILE="${ENV_FILE:-k8s/infra/.env.k3s}"
NAMESPACE="${NAMESPACE:-dev}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE 가 없습니다. .env.k3s.example 을 복사 후 값 채우세요."
  exit 1
fi

# .env 로드 (set -a 로 자동 export)
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "[1/6] postgres-secret (DB 접속 정보)"
kubectl create secret generic postgres-secret \
  --namespace="$NAMESPACE" \
  --from-literal=DB_USERNAME="$DB_USERNAME" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[2/7] jwt-secret (RSA 키는 .env.k3s 의 \\n 을 진짜 줄바꿈으로 변환 후 PEM 파일로 등록)"

# JWT 키들을 임시 PEM 파일로 추출 (printf %b 가 \n 을 진짜 줄바꿈으로 변환)
TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

printf '%b' "$JWT_PRIVATE_KEY" > "$TMP_DIR/jwt-private.pem"
printf '%b' "$JWT_PUBLIC_KEY"  > "$TMP_DIR/jwt-public.pem"
printf '%b' "$JWT_TOKEN_PUBLIC" > "$TMP_DIR/jwt-token-public.pem"

kubectl create secret generic jwt-secret \
  --namespace="$NAMESPACE" \
  --from-file=JWT_PRIVATE_KEY="$TMP_DIR/jwt-private.pem" \
  --from-file=JWT_PUBLIC_KEY="$TMP_DIR/jwt-public.pem" \
  --from-file=JWT_TOKEN_PUBLIC="$TMP_DIR/jwt-token-public.pem" \
  --from-literal=JWT_ACCESS_TOKEN_EXPIRY="$JWT_ACCESS_TOKEN_EXPIRY" \
  --from-literal=JWT_REFRESH_TOKEN_EXPIRY="$JWT_REFRESH_TOKEN_EXPIRY" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[3/7] streaming-secret (streaming-service 전용)"
kubectl create secret generic streaming-secret \
  --namespace="$NAMESPACE" \
  --from-literal=STREAMING_JWT_SECRET="$STREAMING_JWT_SECRET" \
  --from-literal=STREAMING_PUBLIC_BASE_URL="$STREAMING_PUBLIC_BASE_URL" \
  --from-literal=STREAMING_ADDRESS_ADAPTER="$STREAMING_ADDRESS_ADAPTER" \
  --from-literal=STORAGE_S3_PATH="$STORAGE_S3_PATH" \
  --from-literal=CLIENT_CREATOR_BASE_URL="$CLIENT_CREATOR_BASE_URL" \
  --from-literal=KAFKA_CONSUMER_GROUP_ID="$KAFKA_CONSUMER_GROUP_ID_STREAMING" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[4/7] toss-secret (Toss Payments)"
kubectl create secret generic toss-secret \
  --namespace="$NAMESPACE" \
  --from-literal=TOSS_PAYMENT_CK="$TOSS_PAYMENT_CK" \
  --from-literal=TOSS_PAYMENT_SECRET="$TOSS_PAYMENT_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[5/7] aws-secret (S3 + IAM, docker-compose 와 동일 변수명)"
kubectl create secret generic aws-secret \
  --namespace="$NAMESPACE" \
  --from-literal=AWS_ACCESS_KEY="$AWS_ACCESS_KEY" \
  --from-literal=AWS_SECRET_KEY="$AWS_SECRET_KEY" \
  --from-literal=AWS_REGION="$AWS_REGION" \
  --from-literal=AWS_S3_BUCKET="$AWS_S3_BUCKET" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[6/7] openai-secret (ai-service)"
kubectl create secret generic openai-secret \
  --namespace="$NAMESPACE" \
  --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[7/7] user-secret (Google OAuth + Mail)"
kubectl create secret generic user-secret \
  --namespace="$NAMESPACE" \
  --from-literal=GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  --from-literal=GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET" \
  --from-literal=GOOGLE_REDIRECT_URI="$GOOGLE_REDIRECT_URI" \
  --from-literal=MAIL_USERNAME="$MAIL_USERNAME" \
  --from-literal=MAIL_PASSWORD="$MAIL_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "✅ 모든 Secret 등록 완료 (7개). 확인: kubectl get secret -n $NAMESPACE"
