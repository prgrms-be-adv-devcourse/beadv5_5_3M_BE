# Kubernetes(EKS) 기반 AWS 배포 설계서

- **작성일**: 2026-04-22
- **대상 프로젝트**: beadv5_5_3M_BE (영화 스트리밍 MSA, 8개 서비스)
- **작성자**: y0000h2
- **상태**: 초안 (사용자 검토 대기)

---

## 1. 배경 및 목적

### 1.1 현재 운영 구조 (Before)

```
GitHub Actions (CI/CD)
    │
    ├─ ci.yml: PR → 변경된 *-service matrix 빌드/테스트
    │
    └─ cd.yml: main/dev/main push → JAR 빌드 → Docker Hub push
                                    └─ SCP+SSH → EC2 단일 인스턴스
                                                  └─ docker compose --profile {svc} up -d
```

- **문제점**:
  - EC2 단일 인스턴스 → 한 서비스 OOM 시 전체 영향
  - 수동 스케일링 (HPA 없음)
  - 무중단 배포 부재 (재기동 시 다운타임)
  - 서비스 간 격리 약함 (같은 네트워크, 같은 호스트 자원 공유)
  - AI 서비스 추가 시 EC2 자원 한계 부딪힘

### 1.2 전환 목표 (After)

- **AWS EKS** 클러스터로 8개(+α) 서비스 배포
- **기존 GitHub Actions CI/CD 95% 재활용** (마지막 deploy step 만 교체)
- **장애 격리 / 무중단 배포 / 자동 복구** 확보
- **AI 서비스 신설 / review→user 통합** 같은 서비스 단위 변동에 유연한 매니페스트 구조
- **학습 단계** 가성비 우선 (월 약 $277, 시연 후 cluster delete 로 정리)

### 1.3 스코프

**포함:**
- EKS 클러스터 1개 (네임스페이스 `dev`, `prod` 분리. 초기에는 `dev` 만)
- 8개 Spring Boot 서비스 K8s 배포 매니페스트 (Helm 차트)
- in-cluster Redis, Elasticsearch
- AWS RDS (PostgreSQL 단일 인스턴스, 7개 DB)
- ALB Ingress + ACM (도메인 결정 후)
- AWS Secrets Manager + External Secrets Operator
- GitHub Actions `cd.yml` 의 deploy step 교체

**제외 (별도 작업):**
- 모니터링 스택 (Prometheus/Grafana) — 기본 동작 확인 후 별도 PR
- ArgoCD 등 GitOps 도구 — 다음 분기 과제
- AI 서비스 자체 구현 — 본 설계는 "들어왔을 때를 위한 자리만" 마련
- Vercel 프론트엔드 변경 — 백엔드 ALB DNS 등록 정도만

---

## 2. 핵심 설계 결정 (Q1-Q9)

| # | 영역 | 결정 | 핵심 근거 |
|---|---|---|---|
| Q1 | 배포 환경 | **AWS EKS** | 팀 CI/CD 패턴 그대로 살리며 `deploy-to-ec2` step 만 교체. 실무 표준, 멘토 어필 |
| Q2 | DB 전략 | **AWS RDS 단일 인스턴스 (db.t3.medium) + 7~8개 DB** | docker-compose 호환 (DB_HOST 만 RDS endpoint 로). EKS는 Stateless 워크로드 집중 |
| Q3 | Stateful 인프라 | **하이브리드**: 외부 Kafka 그대로 + in-cluster Redis(Bitnami) + ES(ECK) | Kafka 팀 공용 → 옮길 이유 없음. Redis/ES 는 학습 + 비용 절감 |
| Q3.5 | 노드 풀 | **t3.large × 2대 + Cluster Autoscaler** (총 14GB allocatable) | ES 까지 빠듯 들어감. 부족 시 자동 노드 추가 |
| Q4 | 매니페스트 도구 | **Helm 단일 차트 + values per service** | 8개 쌍둥이 서비스에 최적. 외부 차트(Bitnami/ECK) 도구 통일 |
| Q5 | 배포 방식 | **GitHub Actions + helm upgrade 직접 (Push)** | 기존 흐름 유지. ArgoCD 는 다음 분기 |
| Q6 | Ingress | **AWS Load Balancer Controller + ALB Ingress** | EKS 정석. ACM 자동 SSL. 도메인 미정 시 ALB raw DNS 로 시작 |
| Q7 | Secret 관리 | **External Secrets Operator + AWS Secrets Manager** | AWS 통합. 키 rotation 자동화. 멘토 어필 |
| Q8 | AI 서비스 | **앱은 Helm 차트 (다른 서비스와 동일) + 배치는 K8s CronJob 분리** | K-means 인덱싱 등 배치성 작업 분리 |
| Q9 | 환경 분리 | **단일 클러스터 + 네임스페이스 분리** (`dev` 우선) | 학습 단계엔 EKS 2개는 과투자. NetworkPolicy 로 격리 |

### 2.1 폴백 시나리오 (사전 합의)

| 트리거 | 폴백 방법 |
|---|---|
| ES 메모리 부족 (OOMKilled 반복) | ES Deployment `replicas: 0` → movie-service 환경변수 `MOVIE_ES_ENABLED=false` → JPA 폴백 (PR #133 에서 이미 구현됨) |
| 노드 메모리 부족 | Cluster Autoscaler 가 t3.large 1대 자동 추가 |
| 비용 부담 ↑ | EKS 클러스터 종료 후 k3s on EC2 또는 docker-compose 회귀 |

---

## 3. 목표 아키텍처

### 3.1 전체 그림

```
                          [User Browser]
                                │
                                ▼
                     [Vercel Frontend]
                                │ HTTPS
                                ▼
                        [Route 53]  ← (도메인 결정 후)
                                │
                                ▼
                      [AWS ALB] (ACM SSL 자동)
                                │
        ┌───────────────────────┴────────────────────────┐
        │            EKS Cluster (1개)                   │
        │  ┌──────────── Namespace: dev ──────────────┐ │
        │  │                                          │ │
        │  │  Ingress → gateway-service (8000)         │ │
        │  │      ├─→ creator-service   (8080)         │ │
        │  │      ├─→ payment-service   (8081)         │ │
        │  │      ├─→ settlement-service(8083)         │ │
        │  │      ├─→ ticket-service    (8084)         │ │
        │  │      ├─→ user-service      (8085)         │ │
        │  │      ├─→ movie-service     (8086)         │ │
        │  │      ├─→ review-service    (8087)         │ │
        │  │      └─→ ai-service        (포트 미정, 예정) │ │
        │  │                                           │ │
        │  │  ─── Stateful (in-cluster) ───            │ │
        │  │  ├─ Redis      (Bitnami StatefulSet)      │ │
        │  │  └─ Elasticsearch + nori (ECK Operator)   │ │
        │  │                                           │ │
        │  │  ─── 운영 컴포넌트 ───                     │ │
        │  │  ├─ AWS Load Balancer Controller          │ │
        │  │  ├─ External Secrets Operator             │ │
        │  │  ├─ Cluster Autoscaler                    │ │
        │  │  └─ CronJob (AI 배치, 추후)               │ │
        │  └───────────────────────────────────────────┘ │
        │                                                │
        │  Worker Nodes: t3.large × 2 (Auto-scaling)    │
        └────────────────────────────────────────────────┘
                  │                    │
                  │                    ▼
                  │          [AWS RDS PostgreSQL]
                  │          db.t3.medium
                  │          ├─ creator_db
                  │          ├─ user_db
                  │          ├─ movie_db
                  │          ├─ payment_db
                  │          ├─ ticket_db
                  │          ├─ settlement_db
                  │          └─ review_db
                  │
                  ├─→ [AWS Secrets Manager] (JWT, Toss, RDS, S3)
                  ├─→ [AWS S3] (조장 계정, 프로필 이미지)
                  └─→ [외부 Kafka 52.78.88.98:9092] (팀 공용)
```

### 3.2 네트워크 흐름

| 외부 → 내부 | 경로 |
|---|---|
| 사용자 API 요청 | Browser → Vercel → ALB(443) → gateway-service Pod(8000) → 내부 서비스 |
| 인증/인가 | gateway-service → user-service / creator-service (HTTP, ClusterIP) |
| 결제 | payment-service → Toss Payments API (외부 HTTPS) |
| 비동기 이벤트 | 모든 서비스 ↔ 외부 Kafka (52.78.88.98:9092, NAT Gateway 통과) |
| 데이터베이스 | 모든 서비스 → RDS Endpoint (5432, VPC 내부) |
| 파일 업로드 | user-service → AWS S3 (HTTPS, IAM 인증) |
| 검색 | movie-service → Elasticsearch (ClusterIP, in-cluster) |

---

## 4. 디렉토리 구조 (신설)

```
beadv5_5_3M_BE/
├── k8s/                          # ★ 신설
│   ├── README.md                 # 운영 가이드
│   ├── charts/
│   │   ├── microservice/         # 8개 서비스 공통 Helm 차트
│   │   │   ├── Chart.yaml
│   │   │   ├── values.yaml       # 기본값
│   │   │   └── templates/
│   │   │       ├── deployment.yaml
│   │   │       ├── service.yaml
│   │   │       ├── hpa.yaml
│   │   │       ├── configmap.yaml
│   │   │       ├── ingress.yaml         # gateway 만 enabled
│   │   │       ├── externalsecret.yaml  # ESO 와 연동
│   │   │       └── _helpers.tpl
│   │   │
│   │   └── batch-job/            # CronJob/Job 공통 차트 (AI 배치용)
│   │       ├── Chart.yaml
│   │       ├── values.yaml
│   │       └── templates/
│   │           ├── cronjob.yaml
│   │           └── job.yaml
│   │
│   ├── values/                   # 서비스별 values (배포 단위)
│   │   ├── values-gateway.yaml
│   │   ├── values-user.yaml
│   │   ├── values-creator.yaml
│   │   ├── values-movie.yaml
│   │   ├── values-payment.yaml
│   │   ├── values-ticket.yaml
│   │   ├── values-settlement.yaml
│   │   ├── values-review.yaml
│   │   └── (values-ai.yaml)      # AI 서비스 신설 시 추가
│   │
│   ├── infra/                    # 1회 설치 컴포넌트 (Helm 외부 차트 + 직접 매니페스트)
│   │   ├── 00-namespace.yaml
│   │   ├── 01-aws-lb-controller.sh    # helm install 스크립트
│   │   ├── 02-external-secrets.sh
│   │   ├── 03-cluster-autoscaler.sh
│   │   ├── 04-redis.sh                # Bitnami Redis Helm
│   │   ├── 05-elasticsearch.sh        # ECK Operator + Cluster
│   │   └── 06-secretstore.yaml        # ESO ↔ AWS Secrets Manager 연결
│   │
│   └── aws/                      # 인프라 코드 (수동 또는 IaC 추후)
│       ├── eksctl-cluster.yaml   # 클러스터 정의
│       ├── rds-init.sql          # 7개 DB 생성 스크립트
│       └── secrets-manager-seed.sh  # 초기 시크릿 등록
│
├── .github/
│   └── workflows/
│       └── cd.yml                # ★ 수정 (deploy-to-ec2 → deploy-to-k8s)
│
└── (기존 *-service/ 디렉토리들은 변경 없음)
```

**원칙:**
- **`k8s/charts/microservice/` 는 절대 서비스별 분기 없음** — 차이는 모두 `values/values-*.yaml` 에서 처리
- 서비스 추가 = `values/values-NEW.yaml` 1개 추가
- 서비스 제거 = `values/values-OLD.yaml` 1개 삭제 + `helm uninstall OLD`

---

## 5. CI/CD 변경 계획

### 5.1 변경 대상

| 파일 | 변경 |
|---|---|
| `.github/workflows/ci.yml` | **변경 없음** (PR 시 빌드/테스트 흐름 그대로) |
| `.github/workflows/build-test-service.yml` | **변경 없음** |
| `.github/workflows/cd.yml` | **deploy-to-ec2 job 교체** → deploy-to-k8s |
| `.github/workflows/pr-review.yml` | **변경 없음** |
| `.github/scripts/detect-changed-services-cicd.sh` | **변경 없음** |
| `docker-compose.yml` | **유지** (로컬 개발용으로 계속 사용) |

### 5.2 새 deploy-to-k8s job (개념 미리보기)

```yaml
deploy-to-k8s:
  needs: [detect-changes, docker-build-push]
  if: needs.detect-changes.outputs.has_changes == 'true'
  runs-on: ubuntu-latest
  strategy:
    fail-fast: false
    matrix:
      service: ${{ fromJson(needs.detect-changes.outputs.services) }}
  steps:
    - uses: actions/checkout@v4

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ap-northeast-2

    - name: Update kubeconfig
      run: aws eks update-kubeconfig --name beadv5-cluster --region ap-northeast-2

    - name: Setup Helm
      uses: azure/setup-helm@v4

    - name: Helm upgrade
      run: |
        # service 디렉토리명에서 -service 빼기 (e.g., movie-service → movie)
        SVC_SHORT=$(echo "${{ matrix.service }}" | sed 's/-service$//')

        helm upgrade --install ${{ matrix.service }} ./k8s/charts/microservice \
          --namespace dev \
          --create-namespace \
          -f ./k8s/values/values-${SVC_SHORT}.yaml \
          --set image.repository=${{ secrets.DOCKERHUB_USERNAME }}/${{ matrix.service }} \
          --set image.tag=${{ github.sha }} \
          --wait --timeout 5m
```

### 5.3 GitHub Secrets 추가/제거

| 액션 | Secret |
|---|---|
| **유지** | `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GEMINI_API_KEY` |
| **추가** | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (조장에게 받은 키) |
| **제거** (사용 안 함) | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` |

---

## 6. AWS 인프라 사전 준비 (1회 작업)

| # | 작업 | 도구 | 비용 시작 |
|---|---|---|---|
| 1 | EKS 클러스터 생성 (`beadv5-cluster`) | `eksctl create cluster -f aws/eksctl-cluster.yaml` | 시간당 $0.10 |
| 2 | t3.large × 2 노드그룹 생성 | (1번에 포함) | 시간당 $0.0832 × 2 |
| 3 | RDS 인스턴스 생성 (db.t3.medium, ap-northeast-2a, public access OFF) | AWS 콘솔 또는 Terraform | 시간당 $0.082 |
| 4 | RDS 7개 DB 스키마 생성 | `psql -f aws/rds-init.sql` | $0 |
| 5 | Secrets Manager 에 시크릿 5개 등록 | `aws secretsmanager create-secret ...` | 월 $0.40 × 5 |
| 6 | IAM Role for ServiceAccount (IRSA) 설정 | `eksctl create iamserviceaccount` | $0 |
| 7 | VPC 보안그룹: EKS 노드 → RDS 5432 허용 | (3번에 포함) | $0 |
| 8 | (선택) Route 53 도메인 등록 / 기존 도메인 NS 변경 | 도메인 결정 후 | 도메인 비용 |
| 9 | ACM 인증서 발급 (도메인 기준) | (8번 후) | $0 |

**예상 1회 셋업 시간**: 4~6시간 (시행착오 포함)

---

## 7. 비용 예상 (월간, 시연 기간)

| 항목 | 단가 | 월 비용 |
|---|---|---|
| EKS 컨트롤 플레인 | $0.10/시간 | $73 |
| 워커 노드 t3.large × 2 | $0.0832/시간 × 2 | $120 |
| RDS db.t3.medium | $0.082/시간 | $60 |
| ALB | $0.0225/시간 + LCU | ~$22 |
| Secrets Manager (5개) | $0.40/시크릿 | $2 |
| EBS gp3 (워커노드 디스크 40GB × 2) | $0.08/GB | ~$7 |
| 데이터 전송 (ALB ↔ Internet, 학습 트래픽) | 첫 100GB $0.09/GB | ~$5 |
| **총합** | | **약 $289/월** |

**비용 절감 옵션** (필요 시):
- 노드 1대로 축소 (개발 시): -$60/월
- Spot Instance (워커 노드 70% 할인): -$80/월
- 시연/개발 안 할 때 cluster delete: 시간 단위 과금만 발생

---

## 8. 마이그레이션 단계 (High-Level)

세부 구현 단계는 별도 implementation plan 참조.

### Phase 0. 사전 정리 (브랜치 첫 커밋)
- `.claude/settings.local.json` 추적 제거 (메모 [project_k8s_upcoming.md](../../../.claude/projects/c--Users-y00h-Downloads-programmers-project-beadv5-5-3M-BE/memory/project_k8s_upcoming.md) 참고)
- `k8s/` 디렉토리 + `.gitkeep` 만 만들기

### Phase 1. AWS 인프라 셋업 (수동, 코드 외)
- EKS 클러스터, RDS, Secrets Manager, IAM 셋업
- 각 단계 검증 (kubectl get nodes / psql 접속 / aws secretsmanager get-secret-value)

### Phase 2. K8s 인프라 컴포넌트 설치
- AWS Load Balancer Controller
- External Secrets Operator + SecretStore (AWS Secrets Manager 연동)
- Cluster Autoscaler
- Bitnami Redis (Helm)
- ECK Operator + Elasticsearch + nori 플러그인 (init container)

### Phase 3. Helm 차트 작성
- `charts/microservice/` 공통 차트 (templates 6개)
- `values/values-*.yaml` 8개

### Phase 4. 단일 서비스 시범 배포 (movie-service)
- DB_HOST 환경변수만 RDS endpoint 로 바꿔서 검증
- Pod Ready, ALB 헬스체크 통과 확인
- 외부에서 `/api/movies` 호출 성공 확인

### Phase 5. 나머지 7개 서비스 순차 배포
- 의존성 순서: user → creator → movie → ticket → payment → settlement → review → gateway
- 각 서비스마다 헬스체크 + 핵심 API 1개 smoke test
- **주의**: 디렉토리명이 `reivew-service` (오타) 임. detect-changes 스크립트와 values 파일 매칭 시 그대로 사용

### Phase 6. CI/CD 워크플로우 교체
- `cd.yml` 수정 PR
- 테스트 PR 머지 → GitHub Actions 가 helm upgrade 자동 실행 확인

### Phase 7. (선택) 도메인 + ACM 인증서 적용
- Route 53 또는 외부 도메인 등록
- ACM 인증서 발급 + ALB Ingress annotation 추가

### Phase 8. (선택) prod 네임스페이스 추가
- `values-prod/` 디렉토리 분기
- main 브랜치 머지 시 prod 네임스페이스 배포 트리거

---

## 9. 위험 요소 및 대응

| 위험 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| 받은 AWS Key 에 EKS 권한 없음 | 중 | 높음 | 조장에게 권한 추가 요청, 또는 본인 계정 신규 |
| ES 메모리 부족 → 노드 OOM | 중 | 중 | `MOVIE_ES_ENABLED=false` 폴백 + ES 0 replica |
| 외부 Kafka 연결 실패 (보안그룹/NAT) | 중 | 높음 | EKS Egress 에서 52.78.88.98:9092 명시 허용 |
| RDS 비용 누적 (잊고 두는 경우) | 높 | 중 | 시연 후 RDS 스냅샷 → 인스턴스 삭제 (스냅샷은 거의 무료) |
| Helm 차트 첫 시도에서 디버깅 시간 소모 | 높 | 낮 | movie-service 1개로만 먼저 검증, 그 후 나머지 |
| AI 서비스 사양 미정 → 차트 변경 필요 | 높 | 낮 | values-ai.yaml 추가만으로 대응 가능한 구조 |

---

## 10. 미해결 / 미래 작업

- [ ] **AWS Key 권한 검증** — 사용자가 조장에게 확인 또는 새 계정 생성
- [ ] **도메인 결정** — 없으면 ALB raw DNS 로 시작, 결정 후 ingress.hosts 한 줄 수정
- [ ] **모니터링 스택** (Prometheus/Grafana/Loki) — 본 배포 안정화 후 별도 PR
- [ ] **ArgoCD 도입** (다음 분기) — GitOps 전환
- [ ] **AI 서비스 명세 확정 시** — `values-ai.yaml` + (필요 시) GPU 노드 풀 추가
- [ ] **review→user 통합 시** — `values-review.yaml` 삭제 + `helm uninstall review-service`
- [ ] **prod 네임스페이스 / 클러스터 분리 검토** — 트래픽 발생 시
- [ ] **사용자 K8s 강의 수강** — 매니페스트 작성 중 또는 후

---

## 11. 참고 자료

**Claude 메모리 (별도 시스템, 본 repo 외)**
- `project_k8s_upcoming.md` — K8s 작업 시작 시 첫 커밋 절차, 팀 CI/CD 파악 결과
- `project_service_evolution.md` — AI 서비스 신설, review→user 통합 가능성
- `project_k8s_design_decisions.md` — Q1-Q9 결정 누적

**Repo 내 파일**
- 기존 CI/CD: [.github/workflows/cd.yml](../../../.github/workflows/cd.yml)
- 기존 Compose: [docker-compose.yml](../../../docker-compose.yml)
- 환경변수 가이드: [docs/env-guide.md](../../env-guide.md)
- 배포 가이드: [docs/DEPLOYMENT.md](../../DEPLOYMENT.md)

**관련 PR**
- #131 ES 인프라 (MovieDocument + ES Repository, nori 분석기)
- #132 ES Kafka Consumer (movie.* 토픽 → ES 색인)
- #133 ES 검색 API (검색/자동완성/필터카운트, **ES OFF 시 JPA 폴백 — K8s 폴백 시나리오의 핵심**)
