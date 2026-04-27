#!/usr/bin/env bash
# Bitnami Redis 설치 스크립트 (단일 master, replica 없음, 인증 없음 — 클러스터 내부 전용)
#
# 실행 전 1회: helm repo add bitnami https://charts.bitnami.com/bitnami && helm repo update
# 사용법:        ./k8s/infra/01-redis.sh
# 접속 endpoint: redis.dev.svc.cluster.local:6379 (별칭 Service, k8s/infra/01b-redis-alias-service.yaml)

set -euo pipefail

helm upgrade --install redis bitnami/redis \
  --namespace dev \
  --create-namespace \
  --version 19.6.4 \
  --set architecture=standalone \
  --set auth.enabled=false \
  --set master.resources.requests.cpu=50m \
  --set master.resources.requests.memory=128Mi \
  --set master.resources.limits.cpu=300m \
  --set master.resources.limits.memory=256Mi \
  --set master.persistence.enabled=true \
  --set master.persistence.size=2Gi \
  --set master.persistence.storageClass=local-path \
  --wait --timeout 5m

echo "Redis 설치 완료. 접속 endpoint: redis.dev.svc.cluster.local:6379 (별칭 Service 적용 후)"
