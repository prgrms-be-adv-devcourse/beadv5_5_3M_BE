# 앱 서비스 배포 가이드

> 선행 조건: 01-setup.md 완료, Secret 생성 완료, ES/Redis 배포 완료

---

## 현재 상태 (2026-04-27 기준)

```
⏳ values 파일 작성 중 (values-ai.yaml만 완료)
⏳ 앱 서비스 미배포
⏳ CI/CD 교체 전
```

---

## 1. 배포 구조 이해

```
k8s/
├── charts/microservice/     ← 공통 Helm 차트 (붕어빵 틀)
│   ├── Chart.yaml
│   ├── values.yaml          ← 기본값
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── configmap.yaml   ← ES_URIS, REDIS_HOST 등 하드코딩됨
│       ├── ingress.yaml
│       └── hpa.yaml
└── values/
    └── values-*.yaml        ← 서비스별 재료 (포트, 이미지, Secret 등)
```

### configmap에 하드코딩된 공통 환경변수

모든 서비스에 자동으로 주입됨 (values 파일에 안 써도 됨):

```yaml
KAFKA_BOOTSTRAP_SERVERS: "<large_PRIVATE_IP>:9092"
REDIS_HOST: "redis.dev.svc.cluster.local"
REDIS_PORT: "6379"
ES_URIS: "http://elasticsearch.dev.svc.cluster.local:9200"
DDL_AUTO: "validate"
```

---

## 2. Secret 이름 매핑

values 파일의 `secrets` 항목은 실제 생성된 Secret 이름과 일치해야 함.

| 내용 | Secret 이름 |
|---|---|
| DB 접속 정보 | `postgres-secret` |
| JWT 키 | `jwt-secret` |
| AWS S3 | `aws-secret` |
| Toss 결제 | `toss-secret` |
| OpenAI | `openai-secret` |

---

## 3. 서비스별 values 파일

### DB_HOST 설정 주의

```yaml
# 현재 (docker-compose 유지)
DB_HOST: "172.31.29.32"   ← large 프라이빗 IP

# 추후 k3s Pod 이전 시
DB_HOST: "postgresql.dev.svc.cluster.local"
```

---

### values-gateway.yaml

```yaml
service:
  name: gateway-service
  port: 8000
  targetPort: 8000
image:
  repository: <DOCKERHUB_USERNAME>/gateway-service
resources:
  requests: { cpu: 200m, memory: 384Mi }
  limits:   { cpu: 1000m, memory: 512Mi }
env:
  CREATOR_SERVICE_URL:    "http://creator-service.dev.svc.cluster.local:8080"
  MOVIE_SERVICE_URL:      "http://movie-service.dev.svc.cluster.local:8086"
  PAYMENT_SERVICE_URL:    "http://payment-service.dev.svc.cluster.local:8081"
  TICKET_SERVICE_URL:     "http://ticket-service.dev.svc.cluster.local:8084"
  USER_SERVICE_URL:       "http://user-service.dev.svc.cluster.local:8085"
  SETTLEMENT_SERVICE_URL: "http://settlement-service.dev.svc.cluster.local:8083"
  STREAMING_SERVICE_URL:  "http://streaming-service.dev.svc.cluster.local:8088"
  AI_SERVICE_URL:         "http://ai-service.dev.svc.cluster.local:8089"
secrets:
  - jwt-secret
ingress:
  enabled: true
```

### values-user.yaml

```yaml
service:
  name: user-service
  port: 8085
  targetPort: 8085
image:
  repository: <DOCKERHUB_USERNAME>/user-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "user_db"
secrets:
  - postgres-secret
  - jwt-secret
  - aws-secret
```

### values-creator.yaml

```yaml
service:
  name: creator-service
  port: 8080
  targetPort: 8080
image:
  repository: <DOCKERHUB_USERNAME>/creator-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "creator_db"
secrets:
  - postgres-secret
  - jwt-secret
  - aws-secret
```

### values-movie.yaml

```yaml
service:
  name: movie-service
  port: 8086
  targetPort: 8086
image:
  repository: <DOCKERHUB_USERNAME>/movie-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "creator_db"
  MOVIE_ES_ENABLED: "true"
secrets:
  - postgres-secret
```

### values-payment.yaml

```yaml
service:
  name: payment-service
  port: 8081
  targetPort: 8081
image:
  repository: <DOCKERHUB_USERNAME>/payment-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "payment_db"
secrets:
  - postgres-secret
  - toss-secret
```

### values-ticket.yaml

```yaml
service:
  name: ticket-service
  port: 8084
  targetPort: 8084
image:
  repository: <DOCKERHUB_USERNAME>/ticket-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "ticket_db"
secrets:
  - postgres-secret
```

### values-settlement.yaml

> **주의**: settlement-service는 JVM 옵션 없음 → OOMKilled 위험
> Dockerfile에 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` 추가 필수

```yaml
service:
  name: settlement-service
  port: 8083
  targetPort: 8083
image:
  repository: <DOCKERHUB_USERNAME>/settlement-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "settlement_db"
secrets:
  - postgres-secret
```

### values-streaming.yaml

```yaml
service:
  name: streaming-service
  port: 8088
  targetPort: 8088
image:
  repository: <DOCKERHUB_USERNAME>/streaming-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "streaming_db"
  KAFKA_CONSUMER_GROUP_ID: "streaming-service"
secrets:
  - postgres-secret
```

### values-ai.yaml (완료)

```yaml
service:
  name: ai-service
  port: 8089
  targetPort: 8089
image:
  repository: <DOCKERHUB_USERNAME>/ai-service
resources:
  requests: { cpu: 200m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 640Mi }
env:
  DB_HOST: "172.31.29.32"
  DB_NAME: "ai_db"
  KAFKA_CONSUMER_GROUP_ID: "ai-service"
secrets:
  - postgres-secret
  - openai-secret
```

---

## 4. 수동 배포 (CI/CD 교체 전)

배포 순서: 의존성 고려

```
user → creator → ticket → payment → settlement → streaming → ai → gateway
```

각 서비스 배포 명령:

```bash
SERVICE=user-service
SHORT=$(echo $SERVICE | sed 's/-service$//')

helm upgrade --install $SERVICE k8s/charts/microservice \
  -f k8s/values/values-$SHORT.yaml \
  --set image.tag=<커밋SHA> \
  --namespace dev \
  --wait --timeout 5m

# 상태 확인
kubectl get pods -n dev -l app.kubernetes.io/name=$SERVICE
kubectl logs -n dev -l app.kubernetes.io/name=$SERVICE --tail=30
```

### 외부 접근 확인 (gateway 배포 후)

```bash
curl -s http://<large_PUBLIC_IP>/actuator/health
```

---

## 5. CI/CD 교체 (GitHub Actions)

### 변경 내용

`.github/workflows/cd.yml`의 deploy step만 교체:

```yaml
# 기존 (SCP + SSH + docker-compose)
- name: Deploy
  run: |
    ssh ubuntu@$EC2_HOST "cd /app && docker compose up -d"

# 변경 후 (helm upgrade)
- name: Setup kubeconfig
  run: |
    mkdir -p ~/.kube
    echo "${{ secrets.KUBECONFIG }}" > ~/.kube/config

- name: Helm upgrade
  run: |
    SVC_SHORT=$(echo "${{ matrix.service }}" | sed 's/-service$//')
    helm upgrade --install ${{ matrix.service }} ./k8s/charts/microservice \
      -f ./k8s/values/values-${SVC_SHORT}.yaml \
      --set image.tag=${{ github.sha }} \
      --namespace dev \
      --wait --timeout 5m
```

### GitHub Secrets 변경

| 액션 | Secret |
|---|---|
| 유지 | `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` |
| 추가 | `KUBECONFIG` (medium의 ~/.kube/config 내용, server 주소를 PUBLIC IP로 변경) |
| 제거 | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` |

### KUBECONFIG 준비 (medium에서)

```bash
cat ~/.kube/config
# server: https://127.0.0.1:6443 →
# server: https://<medium_PUBLIC_IP>:6443 으로 수정 후
# GitHub Secrets → KUBECONFIG에 등록
```

---

## 6. 폴백 시나리오

| 상황 | 대응 |
|---|---|
| ES OOMKilled 반복 | `--set env.MOVIE_ES_ENABLED=false` 로 JPA 폴백 |
| k3s 도입 실패 전체 | docker-compose 회귀 (코드 변경 0, 환경변수만 원복) |
| Pod 롤링 재시작 | `kubectl rollout restart deployment/<서비스명> -n dev` |
