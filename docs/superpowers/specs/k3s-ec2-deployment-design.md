# K3s on EC2(단일 노드) 기반 AWS 배포 설계서

- **작성일**: 2026-04-24
- **대상 프로젝트**: beadv5_5_3M_BE (영화 스트리밍 MSA, 활성 9개 서비스)
- **작성자**: y0000h2
- **상태**: 초안 (사용자 검토 대기)

---

## 1. 배경 및 목적

### 1.1 EKS 설계 폐기 사유

2026-04-22 작성된 EKS 설계서는 다음 자원 가정 위에 만들어졌다:
- t3.large × 2 + Cluster Autoscaler
- RDS db.t3.medium
- ALB + AWS Secrets Manager + ESO
- 월 약 $280 비용

**실제 제공받은 자원**(2026-04-24 확인):
| 항목 | 한도 |
|---|---|
| 리전 | us-east-1 또는 ap-northeast-2 |
| EC2 | **1대만** (최대 t3.large) |
| EBS | 50 GB gp3 |
| Elastic IP | 인스턴스당 1개 |
| VPC / Internet Gateway | 1개 |
| S3 | 범용 버킷 1개 |
| RDS | **미포함** |

→ EKS 컨트롤플레인($73/월) + 추가 노드 + RDF 모두 불가. 단일 EC2 위에 경량 K8s 배포판인 **K3s** 를 얹는 것이 유일한 현실적 선택.

### 1.2 전환 목표 (After)

- **K3s** 단일 노드 클러스터 (경량 K8s 배포판, Rancher 개발, CNCF Sandbox)
- **기존 GitHub Actions CI/CD 95% 재활용** (마지막 deploy step 만 SSH+helm upgrade로 교체)
- **K8s 매니페스트 학습/포트폴리오 가치 확보** (매니페스트 100% K8s 호환)
- **추가 비용 0** (이미 받은 자원만 사용)
- **시연 후 EC2 정지로 즉시 비용 정리 가능**

### 1.3 K3s 선택 근거 (vs 풀버전 K8s)

| 항목 | 풀버전 K8s | K3s |
|---|---|---|
| 컨트롤플레인 메모리 | 1.5 ~ 2 GB | **~ 500 MB** |
| 설치 | kubeadm + CNI + Ingress + 스토리지 (반나절) | **`curl ...sh` 한 줄 (1분)** |
| 매니페스트 호환성 | - | **100%** (Deployment, Service, Ingress, Helm 등 동일) |
| 8 GB EC2 환경 | OOM 위험 큼 | 빠듯하지만 가능 |
| 학습 가치 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (동일) |

→ 향후 풀버전 K8s/EKS 로 옮길 때 매니페스트는 그대로, values 파일만 수정.

### 1.4 스코프

**포함 (활성 9개 서비스 K3s 배포):**
| # | 서비스 | 포트 | DB 스키마 | 메모리 limit |
|---|---|---|---|---|
| 1 | gateway-service | 8000 | (none, 라우팅) | 512Mi |
| 2 | user-service | 8085 | user_db | 512Mi |
| 3 | creator-service | 8080 | creator_db | 512Mi |
| 4 | movie-service | 8086 | movie_db | 512Mi (ES OFF, JPA 폴백) |
| 5 | payment-service | 8081 | payment_db | 512Mi |
| 6 | ticket-service | 8084 | ticket_db | 512Mi |
| 7 | settlement-service | 8083 | settlement_db | 512Mi |
| 8 | **streaming-service** | (env STREAMING_PORT) | streaming_db | **1Gi** (HLS 변환 부하) |
| 9 | **ai-service** | 8089 | ai_db (pgvector 가능성) | **1Gi** (OpenAI 호출 + 임베딩 처리) |

**기타 인프라:**
- K3s 단일 노드 클러스터 (네임스페이스 `dev`)
- in-cluster Redis (Bitnami)
- in-cluster PostgreSQL (PVC + EBS)
- pg_dump → S3 일일 백업 CronJob
- Traefik Ingress (K3s 내장) + Elastic IP
- AWS S3 (영상 본체 + HLS 세그먼트 + 프로필 이미지 가정) + IAM User Access Key
- GitHub Actions `cd.yml` deploy step 교체

**제외 (별도 작업 또는 비활성):**
- **review-service** — user-service 로 통합됨 (`reivew-service` 디렉토리는 잔존, docker-compose 미등록). 본 spec 에서 제외
- **Elasticsearch** — 메모리 한계로 미배포 (`MOVIE_ES_ENABLED=false`, JPA 폴백; PR #133 활용)
- 도메인 / Let's Encrypt SSL — 도메인 결정 후 별도 PR
- 모니터링 스택 (Prometheus/Grafana) — 안정화 후 별도 PR
- ArgoCD GitOps — 다음 분기 과제

### 1.5 OOM 위험 사전 고지 (반드시 인지)

활성 9개 서비스 모두 + Postgres + Redis + K3s 컨트롤플레인을 t3.large(8GB) 1대에 모두 올리는 구성은 **상시 OOM 위험권**에 들어갑니다 (자세한 견적은 §2 참조). 다음 폴백을 **사전 합의**합니다:

| 신호 | 1차 대응 | 2차 대응 |
|---|---|---|
| 노드 메모리 90% 초과 / Pod OOMKilled 1회 발생 | swap 사용 확인 + 해당 Pod JVM heap 더 낮춤 | 가장 부하 적은 서비스 1개 일시 `replicas: 0` |
| ai-service 가 OpenAI 동시 호출 폭증 | ai-service `replicas: 0` 임시 중단 | 자원 추가 협상 → ai-service 별도 EC2 |
| streaming-service HLS 변환 중 OOM | streaming-service 만 mem limit 1.5Gi 상향 (다른 서비스 압축) | HLS 처리만 별도 EC2 분리 |
| 다 올렸는데 안정 운영 불가 판정 | 시연용 시나리오에 필요한 서비스만 가동 | docker-compose 폴백 (compose 파일 그대로 보존) |

---

## 2. 자원 견적 (8 GB / 50 GB 분배)

### 2.1 메모리 분배 (총 8 GB) — 9개 서비스 가정

| 항목 | 메모리 (보수적 예측) |
|---|---|
| OS + Docker daemon + 시스템 데몬 | 500 MB |
| **K3s 컨트롤플레인** | 500 MB ~ 1 GB |
| 일반 Spring Boot 7개 (`-Xmx256m` + 오버헤드 ≈ 500 MB) | **3.5 GB** |
| streaming-service (`-Xmx512m`, mem limit 1Gi) | **1 GB** |
| ai-service (`-Xmx512m`, mem limit 1Gi) | **1 GB** |
| Redis (Bitnami 기본값) | 150 MB |
| PostgreSQL (`shared_buffers=128MB`) | 500 MB ~ 1 GB |
| **합계 (이론치)** | **약 7.7 ~ 8.7 GB / 8 GB ← 한계 초과** |

→ **swap 4~8 GB 추가는 선택 아닌 필수.** 그래도 동시 부하 시 OOM 가능.
→ **§1.5 폴백 시나리오** 가 그래서 사전 합의 필수.
→ ES 는 절대 추가 불가.

### 2.2 디스크 분배 (총 50 GB EBS)

| 항목 | 디스크 |
|---|---|
| OS + 도커 이미지 캐시 (9 서비스 × ~1.5 GB) | 약 18 GB |
| PostgreSQL 데이터 (영상 메타 + 사용자 + 결제 + ai pgvector) | 약 7 GB (초기) |
| 컨테이너 로그 (logrotate 필수, 5 GB 상한) | 5 GB |
| 여유 | 약 20 GB |
| **영상 파일 본체 + HLS 세그먼트** | **S3 별도 (EBS 에 절대 저장 X)** |

### 2.3 메모리 보호 정책 (서비스별 차등)

```yaml
# Helm values 기본값 (일반 7개 서비스용)
resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 512Mi

# values-streaming.yaml / values-ai.yaml 오버라이드
resources:
  requests:
    cpu: 200m
    memory: 768Mi
  limits:
    cpu: 1000m
    memory: 1Gi
```

JVM 옵션 (Helm `env` 로 주입):
- 일반 7개: `JAVA_OPTS=-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m`
- streaming/ai 2개: `JAVA_OPTS=-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m`

---

## 3. 핵심 설계 결정 (Q1-Q12)

| # | 영역 | 결정 | 핵심 근거 |
|---|---|---|---|
| Q1 | 배포 환경 | **K3s on EC2 t3.large 1대** | 받은 자원 한계. EKS 불가. 매니페스트 100% K8s 호환 |
| Q2 | 리전 | **ap-northeast-2 (서울)** | 한국 사용자 시연 → 지연 최소 |
| Q3 | DB 전략 | **in-cluster PostgreSQL (PVC + EBS) + S3 일일 백업** | RDS 자원 미수령. 노드 죽음 = 데이터 위험 → CronJob 으로 매일 `pg_dump` → S3 |
| Q4 | Redis | **in-cluster Bitnami Helm** | ElastiCache 비용. 150 MB 메모리로 충분 |
| Q5 | Elasticsearch | **OFF (JPA 폴백)** | 메모리 한계. movie-service `MOVIE_ES_ENABLED=false` (PR #133 폴백 구조 활용) |
| Q6 | Kafka | **외부 EC2 그대로** (`52.78.88.98:9092`) | 팀 공용. 옮길 이유 없음. 본 클러스터에서 producer/consumer 만 |
| Q7 | 매니페스트 도구 | **Helm 단일 차트 + values per service** | EKS 설계 그대로. 8개 쌍둥이 서비스 |
| Q8 | 배포 방식 | **GitHub Actions → SSH (Elastic IP) → `helm upgrade`** | EKS 의 `aws eks update-kubeconfig` 불필요. SSH 단순 |
| Q9 | Ingress / 외부 노출 | **Traefik (K3s 내장) + Elastic IP** | ALB 비용/권한 부재. Traefik 별도 설치 불필요 |
| Q10 | HTTPS / 도메인 | **일단 HTTP + Elastic IP** → 도메인 결정 후 cert-manager + Let's Encrypt | 도메인 미정. 시연 단계 HTTP 허용 |
| Q11 | Secret 관리 | **K8s Secret 직접** (`kubectl create secret generic`) | ESO + AWS Secrets Manager 자원/비용 부담. 단일 노드에 과도 |
| Q12 | S3 인증 | **IAM User Access Key + K8s Secret 주입** | IRSA 같은 IAM-K8s 통합 K3s 에 없음. user-service Pod 가 환경변수로 키 사용 |

### 3.1 폴백 시나리오 (사전 합의)

| 트리거 | 폴백 방법 |
|---|---|
| Pod OOMKilled 반복 | `resources.limits.memory` 상향 + JVM heap 조정. 그래도 안 되면 해당 서비스 일시 disable |
| 노드 메모리 부족 | 가장 덜 중요한 서비스 1개 `replicas: 0` 으로 임시 중단 |
| EBS 디스크 부족 | `docker system prune -a` + 옛 PostgreSQL WAL 정리 + 컨테이너 로그 truncate |
| PostgreSQL 데이터 손상 | S3 백업에서 최근 일자 `pg_restore` |
| K3s 자체 장애 | EC2 재부팅 → K3s systemd 자동 시작 |
| 비용/시연 종료 | EC2 stop → 시간당 과금 즉시 멈춤. EBS 만 월 ~$4 유지 |

---

## 4. 목표 아키텍처

### 4.1 전체 그림

```
                          [User Browser]
                                │
                                ▼
                       [Vercel Frontend]
                                │ HTTPS
                                ▼
                       [Elastic IP : 80]
                                │
                                ▼
        ┌───────────────────────────────────────────────┐
        │   EC2 t3.large (8 GB / 50 GB EBS)             │
        │  Region: ap-northeast-2                        │
        │  ┌─────────── K3s (single-node) ────────────┐ │
        │  │  Namespace: dev                           │ │
        │  │                                           │ │
        │  │  Traefik Ingress (K3s 내장)               │ │
        │  │      │                                    │ │
        │  │      ▼                                    │ │
        │  │  gateway-service (8000)                   │ │
        │  │      ├─→ creator-service   (8080)         │ │
        │  │      ├─→ payment-service   (8081)         │ │
        │  │      ├─→ settlement-service(8083)         │ │
        │  │      ├─→ ticket-service    (8084)         │ │
        │  │      ├─→ user-service      (8085)  ← review 통합 │ │
        │  │      ├─→ movie-service     (8086)  ← ES OFF │ │
        │  │      ├─→ streaming-service (env)   ← HLS 변환 │ │
        │  │      └─→ ai-service        (8089)  ← OpenAI │ │
        │  │                                           │ │
        │  │  ─── Stateful (in-cluster) ───            │ │
        │  │  ├─ Redis      (Bitnami Helm)             │ │
        │  │  └─ PostgreSQL (PVC → local-path)         │ │
        │  │                                           │ │
        │  │  ─── 운영 컴포넌트 ───                     │ │
        │  │  └─ pg-backup-cronjob (매일 02:00 → S3)   │ │
        │  └───────────────────────────────────────────┘ │
        │                                                │
        │  로컬 디스크: /var/lib/rancher/k3s/storage/ (PVC) │
        └───┬─────────────────────────┬──────────────────┘
            │                         │
            ▼                         ▼
       [AWS S3]              [외부 Kafka EC2]
       - 영상 본체            (52.78.88.98:9092)
       - 프로필 이미지        팀 공용
       - pg_dump 백업
```

### 4.2 네트워크 흐름

| 외부 → 내부 | 경로 |
|---|---|
| 사용자 API 요청 | Browser → Vercel → Elastic IP:80 → Traefik → gateway-service Pod |
| 인증/인가 | gateway-service → user-service / creator-service (ClusterIP) |
| 결제 | payment-service → Toss Payments API (외부 HTTPS) |
| 비동기 이벤트 | 모든 서비스 ↔ 외부 Kafka (52.78.88.98:9092) |
| DB | 모든 서비스 → postgres.dev.svc.cluster.local:5432 |
| 파일 업로드 (프로필) | user-service → AWS S3 (HTTPS, IAM Access Key) |
| 영상 업로드 / HLS 세그먼트 | streaming-service → AWS S3 (HTTPS, IAM Access Key) |
| AI 추천 / 임베딩 | ai-service → OpenAI API (외부 HTTPS) + Postgres pgvector |
| 검색 (movie) | movie-service → JPA → PostgreSQL (ES 미사용) |

### 4.3 보안 그룹 (EC2)

| 방향 | 포트 | 출처/대상 | 용도 |
|---|---|---|---|
| Inbound | 22 | 본인 IP / GitHub Actions IP 범위 | SSH 배포 |
| Inbound | 80 | 0.0.0.0/0 | HTTP (시연) |
| Inbound | 443 | 0.0.0.0/0 | HTTPS (도메인 결정 후) |
| Inbound | 6443 | 본인 IP | K3s API (kubectl 로컬 접속용) |
| Outbound | 9092 | 52.78.88.98/32 | 외부 Kafka |
| Outbound | 443 | 0.0.0.0/0 | S3, Toss, Docker Hub, GitHub |

---

## 5. 디렉토리 구조 (신설)

```
beadv5_5_3M_BE/
├── k8s/                                  # ★ 신설
│   ├── README.md                         # 운영 가이드
│   ├── charts/
│   │   └── microservice/                 # 8개 서비스 공통 Helm 차트
│   │       ├── Chart.yaml
│   │       ├── values.yaml               # 기본값
│   │       └── templates/
│   │           ├── deployment.yaml
│   │           ├── service.yaml
│   │           ├── ingress.yaml          # gateway 만 enabled, Traefik IngressClass
│   │           ├── configmap.yaml
│   │           ├── secret.yaml           # K8s Secret 직접 (ESO 미사용)
│   │           ├── hpa.yaml              # 노드 1대라 사실상 inactive (선택적)
│   │           └── _helpers.tpl
│   │
│   ├── values/                           # 서비스별 values (9개)
│   │   ├── values-gateway.yaml
│   │   ├── values-user.yaml              # review 통합 (review API 포함)
│   │   ├── values-creator.yaml
│   │   ├── values-movie.yaml             # MOVIE_ES_ENABLED=false
│   │   ├── values-payment.yaml
│   │   ├── values-ticket.yaml
│   │   ├── values-settlement.yaml
│   │   ├── values-streaming.yaml         # mem 1Gi, S3 키 주입
│   │   └── values-ai.yaml                # mem 1Gi, OpenAI API 키 주입
│   │   # (values-review.yaml 없음 — user 통합)
│   │
│   ├── infra/                            # 1회 설치 컴포넌트
│   │   ├── 00-namespace.yaml
│   │   ├── 01-redis.sh                   # Bitnami Helm install
│   │   ├── 02-postgres.yaml              # in-cluster Postgres + PVC
│   │   ├── 03-secrets.sh                 # kubectl create secret 일괄 (DB pw, JWT, Toss, S3)
│   │   └── 04-traefik-config.yaml        # K3s 내장 Traefik 설정 오버라이드 (필요시)
│   │
│   ├── ops/                              # 운영 자동화
│   │   ├── pg-backup-cronjob.yaml        # 매일 02:00 pg_dump → S3
│   │   └── log-rotate.conf               # /etc/logrotate.d/docker-containers (호스트 설치)
│   │
│   └── aws/                              # AWS 인프라 정의
│       ├── ec2-setup.md                  # EC2 생성 절차 (수동)
│       ├── eip-attach.md                 # Elastic IP 할당 절차
│       └── s3-bucket-policy.json         # S3 버킷 정책 (IAM User 접근)
│
├── .github/
│   └── workflows/
│       └── cd.yml                        # ★ 수정 (deploy-to-ec2 → deploy-to-k3s)
│
└── (기존 *-service/ 디렉토리들은 변경 없음. gateway-service application-prod.yaml 만 라우팅 환경변수 형식 조정)
```

**원칙:**
- `charts/microservice/` 는 절대 서비스별 분기 없음 — 차이는 모두 `values/values-*.yaml` 에서 처리
- 향후 EKS/풀버전 K8s 이전 시 `charts/microservice/` 그대로 재사용. `infra/` 만 갈아끼움

---

## 6. CI/CD 변경 계획

### 6.1 변경 대상

| 파일 | 변경 |
|---|---|
| `.github/workflows/ci.yml` | **변경 없음** (PR 빌드/테스트 흐름 그대로) |
| `.github/workflows/build-test-service.yml` | **변경 없음** |
| `.github/workflows/cd.yml` | **deploy-to-ec2 job 교체** → deploy-to-k3s |
| `.github/workflows/pr-review.yml` | **변경 없음** |
| `.github/scripts/detect-changed-services-cicd.sh` | **변경 없음** |
| `docker-compose.yml` | **유지** (로컬 개발용으로 계속 사용) |
| `gateway-service/.../application-prod.yaml` | 라우팅 URI 환경변수 형식 조정 (BASE_IP+PORT → SERVICE_HOST 단일 변수) |

### 6.2 새 deploy-to-k3s job (개념 미리보기)

```yaml
deploy-to-k3s:
  needs: [detect-changes, docker-build-push]
  if: needs.detect-changes.outputs.has_changes == 'true'
  runs-on: ubuntu-latest
  strategy:
    fail-fast: false
    matrix:
      service: ${{ fromJson(needs.detect-changes.outputs.services) }}
  steps:
    - uses: actions/checkout@v4

    - name: SSH and helm upgrade
      uses: appleboy/ssh-action@v1
      with:
        host: ${{ secrets.EC2_ELASTIC_IP }}
        username: ubuntu
        key: ${{ secrets.EC2_SSH_KEY }}
        script: |
          # 코드 동기화 (k8s/ 디렉토리 최신화)
          cd ~/beadv5_5_3M_BE
          git pull origin ${{ github.ref_name }}

          SVC=${{ matrix.service }}            # e.g., movie-service
          SVC_SHORT=$(echo "$SVC" | sed 's/-service$//')

          helm upgrade --install $SVC ./k8s/charts/microservice \
            --namespace dev \
            --create-namespace \
            -f ./k8s/values/values-${SVC_SHORT}.yaml \
            --set image.repository=${{ secrets.DOCKERHUB_USERNAME }}/$SVC \
            --set image.tag=${{ github.sha }} \
            --wait --timeout 5m
```

### 6.3 GitHub Secrets 추가/제거/유지

| 액션 | Secret |
|---|---|
| **유지** | `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GEMINI_API_KEY`, `EC2_SSH_KEY` |
| **추가** | `EC2_ELASTIC_IP` |
| **제거 / 사용 안 함** | `EC2_HOST` (도메인 형태였다면. EIP 가 대체) |

→ EKS 안에서 추가하기로 했던 `AWS_ACCESS_KEY_ID/SECRET` 은 본 K3s 안에서는 **필요 없음** (kubectl 접근에 SSH 사용). 단, S3 접근용 IAM User Access Key 는 **K3s Secret 으로 클러스터 안에 등록** (GitHub Secret 아님).

---

## 7. AWS 인프라 사전 준비 (1회 작업)

| # | 작업 | 도구 / 위치 | 비용 시작 |
|---|---|---|---|
| 1 | VPC + Subnet + Internet Gateway 생성 | AWS 콘솔 (`ap-northeast-2`) | $0 |
| 2 | 보안 그룹 생성 (5.3 표 참조) | AWS 콘솔 | $0 |
| 3 | EC2 t3.large 1대 생성 (Ubuntu 22.04 LTS, EBS 50GB gp3) | AWS 콘솔 | 시간당 $0.0832 |
| 4 | Elastic IP 할당 + EC2 attach | AWS 콘솔 | $0 (attach 상태) |
| 5 | EC2 SSH 접속 → swap 4 GB 추가 (OOM 방지 보험) | EC2 내부 `fallocate` | $0 |
| 6 | K3s 설치: `curl -sfL https://get.k3s.io \| sh -` | EC2 내부 | $0 |
| 7 | kubectl/helm 로컬 설치 + kubeconfig 복사 (`/etc/rancher/k3s/k3s.yaml` → 본인 PC) | 로컬 + EC2 | $0 |
| 8 | S3 버킷 생성 (`beadv5-uploads` 등 명명) + 버킷 정책 적용 | AWS 콘솔 | $0 + 사용량 |
| 9 | IAM User 생성 (S3 버킷 접근 전용) → Access Key 발급 | AWS IAM | $0 |
| 10 | GitHub Secrets 등록: `EC2_ELASTIC_IP`, (S3 키는 K8s Secret 으로) | GitHub | $0 |

**예상 1회 셋업 시간**: 2~3시간 (시행착오 포함)

---

## 8. 비용 예상 (월간, 시연 기간)

| 항목 | 단가 | 월 비용 |
|---|---|---|
| EC2 t3.large | $0.0832/시간 | **$60 (24/7 가정)** — 받은 자원이라 본인 부담 X |
| EBS gp3 50GB | $0.08/GB | $4 |
| Elastic IP (attach 상태) | $0 | $0 |
| S3 (10 GB 가정) | $0.023/GB | < $1 |
| 데이터 전송 (S3 ↔ Internet, 학습 트래픽) | 첫 100GB $0.09/GB | ~$5 |
| **본인 추가 부담** | | **약 $0** (EC2/EBS 모두 받은 자원) |

**시연 후 정리**: EC2 stop → 시간 단위 과금 멈춤. EBS 만 ~$4/월 잔존. 완전 정리 시 EC2 terminate + EBS delete + S3 빈 후 버킷 delete.

---

## 9. 마이그레이션 단계 (High-Level)

세부 구현 단계는 별도 implementation plan 참조.

### Phase 0. 사전 정리 (브랜치 첫 커밋, 약 5분)
- 브랜치 `feature/k3s-deployment` 생성
- `.claude/settings.local.json` 추적 제거 (메모 [project_k8s_upcoming.md] 참조 — 이미 처리됐을 수 있음)
- `k8s/` 디렉토리 + `.gitkeep` 생성

### Phase 1. EC2 + K3s 셋업 (수동, 약 1시간)
- AWS 콘솔에서 VPC/EC2/EIP/SG 생성
- EC2 SSH 접속 → swap 추가 → K3s 설치
- 로컬 PC 에 `kubectl` + `helm` 설치 + kubeconfig 가져오기
- 검증: `kubectl get nodes` (Ready 상태 확인)

### Phase 2. K3s 인프라 컴포넌트 설치 (약 1시간)
- 네임스페이스 `dev` 생성
- Bitnami Redis Helm install
- in-cluster PostgreSQL 매니페스트 + PVC 적용
- 8개 DB 스키마 생성 (`psql` 로 `creator_db`, `user_db`, `movie_db`, `payment_db`, `ticket_db`, `settlement_db`, `streaming_db`, `ai_db`)
  - ai_db 는 pgvector 익스텐션 활성화 필요 가능성 (담당 조원 확인)
- K8s Secret 일괄 등록 (DB pw, JWT, Toss, S3 Access Key, **OpenAI API Key**, **Streaming JWT Secret**)

### Phase 3. Helm 차트 작성 (약 2시간)
- `charts/microservice/` 차트 1개 (templates 6~7개)
- `values.yaml` 기본값
- 검증: `helm template` 으로 매니페스트 생성 확인

### Phase 4. values 파일 9개 작성 (약 2.5시간)
- 각 서비스별 `values/values-*.yaml`
- 환경변수, 포트, 의존성, 메모리 limit 명시
- streaming/ai 는 1Gi limit + JVM 옵션 차등 적용

### Phase 5. movie-service 시범 배포 (약 1시간)
- `MOVIE_ES_ENABLED=false` 명시
- DB 연결 검증
- Pod Ready, Traefik 라우팅 확인

### Phase 6. 나머지 8개 서비스 + gateway 외부 노출 (약 3시간)
- 의존성 순서: user → creator → movie → ticket → payment → settlement → streaming → ai → gateway
- 각 서비스마다 헬스체크 + 핵심 API 1개 smoke test
- streaming, ai 는 메모리 모니터링 (`kubectl top pod`) 동시에 진행
- gateway-service `application-prod.yaml` 라우팅 환경변수 조정 (review 라우팅을 user-service 로 합친 부분 확인)
- Traefik IngressRoute 적용 → Elastic IP 로 외부 접속 확인

### Phase 7. CI/CD 워크플로우 교체 (약 1시간)
- `cd.yml` 의 deploy-to-ec2 job → deploy-to-k3s 로 교체
- GitHub Secrets 등록 (`EC2_ELASTIC_IP`)
- 테스트 PR 머지 → GitHub Actions 가 SSH+helm upgrade 실행 확인

### Phase 8. 운영 자동화 (약 30분)
- pg_dump → S3 CronJob 적용 (매일 02:00, 7일 retention)
- 호스트 EC2 에 logrotate 설정 (`/etc/logrotate.d/docker-containers`)
- `crontab -e` 로 주간 `docker system prune -af` 등록 (선택)

### (선택) Phase 9. 도메인 + Let's Encrypt SSL
- 도메인 결정 → Route 53 또는 외부 도메인 NS 변경 → Elastic IP 로 A 레코드
- cert-manager 설치 + ClusterIssuer (Let's Encrypt)
- Traefik IngressRoute 에 TLS 추가
- gateway-service CORS 에 도메인 추가

### (선택) Phase 10. ai-service / streaming-service 분리 (OOM 발생 시 폴백)
- 본 클러스터 OOM 빈발 시 → **자원 추가 협상 → 별도 EC2 1대 더 받기**
- 받으면: `k3s agent` 명령으로 워커 노드 join → ai/streaming 두 Pod 만 nodeSelector 로 새 노드 격리

---

## 10. 위험 요소 및 대응

| 위험 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| **9개 서비스 동시 기동 시 OOM** | **높음** | 매우 높음 | swap **8 GB** 사전 추가 + Pod `resources.limits.memory` 차등 (일반 512Mi / streaming·ai 1Gi) + JVM heap 차등 (256m / 512m) + §1.5 폴백 시나리오 |
| ai-service OpenAI 호출 동시성 폭증 | 중 | 매우 높음 | webclient timeout + reactive backpressure. 안 되면 `replicas: 0` 임시 중단 |
| streaming-service HLS 변환이 메모리 1Gi 초과 | 중 | 높음 | mem limit 1.5Gi 상향 + 다른 서비스 압축. 안 되면 별도 EC2 분리 |
| EBS 50 GB 부족 (이미지/로그 누적) | 중 | 중 | logrotate + 주간 `docker system prune -af` + Postgres WAL 정리 |
| EC2 노드 장애 → Postgres 데이터 손실 | 중 | 매우 높음 | 매일 02:00 `pg_dump` → S3, 7일 retention. 복구 절차 README 명시 |
| Elastic IP 변동 (실수로 detach) | 낮 | 높음 | EIP 명시적 attach + 콘솔에서 "release" 권한 IAM 차단 |
| 외부 Kafka 연결 실패 (보안그룹) | 중 | 높음 | 보안그룹 Outbound 9092 명시 허용 + EC2 → 52.78.88.98:9092 telnet 검증 |
| 영상 업로드 트래픽 폭증 → S3 비용↑ | 낮 (시연 단계) | 낮 | S3 Lifecycle 로 30일 후 IA 전환 (선택) |
| K3s 자체 버그 / 업데이트 깨짐 | 낮 | 중 | K3s 버전 고정 (`INSTALL_K3S_VERSION=v1.30.x`) |
| EC2 stop 잊고 비용 누적 | 중 | 낮 | (받은 자원이라 본인 부담 X, but 운영 한도 차감) 시연 후 즉시 stop 습관 |

---

## 11. 미해결 / 미래 작업

- [ ] **EC2 sudo 권한 + SSH 키 확보** — 사용자가 운영 페이지에서 발급 받기
- [ ] **S3 버킷명 결정** — 글로벌 unique 필요 (예: `beadv5-uploads-{팀번호}`)
- [ ] **도메인 결정** — 없으면 Elastic IP raw 로 시작, 결정 후 Phase 9 진행
- [ ] **모니터링 스택** (Prometheus/Grafana/Loki) — 메모리 한계상 본 클러스터 추가 어려움. 외부 SaaS (Grafana Cloud Free Tier) 검토
- [ ] **OOM 발생 패턴 관측 후 자원 추가 협상 결정** — 별도 EC2 1대 더 받으면 ai/streaming 분리
- [ ] **reivew-service 디렉토리 정리** — user 통합 완료 시 디렉토리 자체 git rm (별도 PR)
- [ ] **streaming-service HLS 변환 부하 실측** — 시연 영상 1개 업로드해서 메모리/CPU 피크 측정
- [ ] **ai-service pgvector 익스텐션 활성화 확인** — 담당 조원과 협의
- [ ] **사용자 K3s/K8s 강의 수강** — 매니페스트 작성 중 또는 후

---

## 12. 참고 자료

**Claude 메모리 (별도 시스템, 본 repo 외)**
- `project_k8s_upcoming.md` — K8s 작업 시작 시 첫 커밋 절차, 팀 CI/CD 파악 결과
- `project_service_evolution.md` — AI 서비스 신설, review→user 통합 가능성
- `project_k8s_design_decisions.md` — EKS 시절 Q1-Q9 결정 누적 (본 설계서로 일부 갱신)

**Repo 내 파일**
- 팀 공유용 1페이지 요약: [docs/k8s-team-summary.md](../../k8s-team-summary.md)
- 기존 CI/CD: [.github/workflows/cd.yml](../../../.github/workflows/cd.yml)
- 기존 Compose: [docker-compose.yml](../../../docker-compose.yml)
- 환경변수 가이드: [docs/env-guide.md](../../env-guide.md)
- 배포 가이드: [docs/DEPLOYMENT.md](../../DEPLOYMENT.md)

**관련 PR**
- #131 ES 인프라 (MovieDocument + ES Repository, nori 분석기)
- #132 ES Kafka Consumer (movie.* 토픽 → ES 색인)
- #133 ES 검색 API (검색/자동완성/필터카운트, **ES OFF 시 JPA 폴백 — K3s 환경의 핵심 폴백**)

**외부 문서**
- K3s 공식: https://docs.k3s.io/
- Bitnami Redis Helm chart: https://github.com/bitnami/charts/tree/main/bitnami/redis
- Traefik on K3s: https://docs.k3s.io/networking/networking-services#traefik-ingress-controller
