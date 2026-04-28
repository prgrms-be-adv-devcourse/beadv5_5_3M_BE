# EC2 설치 및 설정 가이드

> kubectl 명령은 항상 **medium에서** 실행

---

## 현재 상태 (2026-04-28 기준)

```
✅ k3s 설치 완료
✅ 클러스터 구성 완료 (medium server + large agent)
✅ namespace: dev 생성 완료
✅ Taint 설정 완료 (medium에 dedicated=es:NoSchedule)
✅ Helm 설치 완료 (medium)
✅ kubeconfig 설정 완료 (medium ~/.kube/config)
✅ Secret 생성 완료
✅ ES 배포 완료 (Running, v9.0.0 + nori 플러그인)
✅ Redis 배포 완료 (Running)
✅ PostgreSQL 배포 완료 (Running, k3s Pod, msauser)
✅ 앱 서비스 9개 배포 완료 (Running)
```

---

## Step 1. k3s 설치

### medium — k3s server 설치

```bash
# medium EC2에 SSH 접속 후
curl -sfL https://get.k3s.io | sh -

# 설치 확인
sudo kubectl get nodes

# large join에 필요한 토큰 메모
cat /var/lib/rancher/k3s/server/node-token
```

### large — k3s agent 설치

```bash
# large EC2에 SSH 접속 후
curl -sfL https://get.k3s.io | \
  K3S_URL=https://<medium_PRIVATE_IP>:6443 \
  K3S_TOKEN=<위에서_복사한_토큰> \
  sh -
```

### 클러스터 확인

```bash
# medium에서
sudo kubectl get nodes

# Expected:
# ip-172-31-42-216   Ready   control-plane,master
# ip-172-31-29-32    Ready   worker
```

---

## Step 2. kubeconfig 설정 (medium)

sudo 없이 kubectl, helm 사용하기 위한 설정.

```bash
# kubeconfig 권한 변경
sudo chmod 644 /etc/rancher/k3s/k3s.yaml

# 표준 위치에 복사 (helm이 여기서 읽음)
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
```

---

## Step 3. Helm 설치 (medium)

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | sudo bash

# 확인
helm version
```

---

## Step 4. namespace 생성

```bash
kubectl create namespace dev

# 확인
kubectl get namespaces
```

---

## Step 5. Taint 설정

medium 노드에 ES 외 Pod 진입 차단.

```bash
# control-plane Taint 제거 (ES Pod가 medium에 뜰 수 있도록)
kubectl taint nodes ip-172-31-42-216 node-role.kubernetes.io/control-plane:NoSchedule-

# ES 전용 Taint 추가
kubectl taint nodes ip-172-31-42-216 dedicated=es:NoSchedule

# 확인
kubectl describe node ip-172-31-42-216 | grep Taint
# Expected: Taints: dedicated=es:NoSchedule
```

---

## Step 6. Secret 생성

```bash
# DB 접속 정보
kubectl create secret generic postgres-secret \
  --from-literal=DB_HOST=<large_PRIVATE_IP> \
  --from-literal=DB_USERNAME=<USERNAME> \
  --from-literal=DB_PASSWORD=<PASSWORD> \
  -n dev

# JWT 키
kubectl create secret generic jwt-secret \
  --from-literal=JWT_PRIVATE_KEY="$(cat jwt-private.pem)" \
  --from-literal=JWT_PUBLIC_KEY="$(cat jwt-public.pem)" \
  -n dev

# Toss 결제
kubectl create secret generic toss-secret \
  --from-literal=TOSS_PAYMENT_SECRET=<SECRET> \
  --from-literal=TOSS_PAYMENT_CK=<CK> \
  -n dev

# S3
kubectl create secret generic aws-secret \
  --from-literal=AWS_ACCESS_KEY=<KEY> \
  --from-literal=AWS_SECRET_KEY=<SECRET> \
  --from-literal=AWS_S3_BUCKET=<BUCKET> \
  -n dev

# OpenAI
kubectl create secret generic openai-secret \
  --from-literal=OPENAI_API_KEY=<KEY> \
  -n dev

# 확인
kubectl get secrets -n dev
```

---

## Step 7. 인프라 배포

### 레포 clone (medium)

```bash
git clone <레포 주소>
cd beadv5_5_3M_BE
```

### ES 배포

```bash
kubectl apply -f k8s/infra/elasticsearch.yaml

# 확인 (이미지 pull + nori 설치로 2~3분 소요)
kubectl get pods -n dev -l app=elasticsearch

# nori 플러그인 확인
kubectl exec -n dev elasticsearch-0 -- curl -s localhost:9200/_nodes/plugins?filter_path=nodes.*.plugins.name
# Expected: {"nodes":{"...":{"plugins":[{"name":"analysis-nori"}]}}}
```

### PostgreSQL 배포 (추후 docker-compose → k3s 이전 시)

```bash
kubectl apply -f k8s/infra/postgresql.yaml

# 확인
kubectl get pods -n dev -l app=postgresql

# 초기화 완료 확인 (init.sh가 DB 생성 + pgvector 활성화 자동 처리)
kubectl exec -n dev sts/postgresql -- psql -U <USERNAME> -c "\l"
```

> PostgreSQL k3s Pod로 이전 완료. DB 유저: `msauser`

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| Pod Pending | Taint 문제 또는 메모리 부족 | `kubectl describe pod <이름> -n dev` Events 확인 |
| helm: command not found | helm 미설치 | Step 3 참고 |
| Kubernetes cluster unreachable | kubeconfig 미설정 | Step 2 참고 |
| ES OOMKilled | 메모리 부족 | `kubectl top nodes` 확인 |
