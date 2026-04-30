# K3s on EC2(단일 노드) 배포 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **For human (사용자):** 이 계획서는 주니어 개발자 눈높이로 쓰여 있습니다. 각 Task 시작에 **💡 이게 뭐냐면** 박스가 있어요. 모르는 단어 나오면 그 박스 먼저 읽어주세요.

**Goal:** EC2 t3.large 1대 위에 K3s 단일 노드 클러스터를 띄우고, 9개 Spring Boot 마이크로서비스를 Helm 차트로 배포한 뒤, GitHub Actions 가 자동으로 `helm upgrade` 하도록 CI/CD 를 교체한다.

**Architecture:** EC2 1대 안에서 K3s(경량 K8s) 가 9개 서비스 + in-cluster Postgres + Redis 를 호스팅. Traefik(K3s 내장 Ingress)이 Elastic IP 80번으로 외부 트래픽을 받아 gateway-service 로 라우팅. 영상/이미지는 S3, 비동기 이벤트는 외부 Kafka(52.78.88.98:9092) 활용.

**Tech Stack:** K3s v1.30.x · Helm 3 · Bitnami Redis (Helm) · in-cluster PostgreSQL 16 · Traefik (K3s 내장) · GitHub Actions · AWS EC2 t3.large (Ubuntu 22.04) · AWS S3 · IAM User Access Key · Docker Hub

**Spec:** [docs/superpowers/specs/k3s-ec2-deployment-design.md](../specs/k3s-ec2-deployment-design.md)

---

## 사전 준비물 체크리스트

작업 시작 전 확인:

- [ ] **AWS 콘솔 접근** — 운영 페이지에서 받은 IAM 계정으로 로그인 가능
- [ ] **AWS CLI 로컬 설치** (선택, 일부 task에서 사용) — Windows: `winget install Amazon.AWSCLI`
- [ ] **kubectl 로컬 설치** — Windows: `winget install -e --id Kubernetes.kubectl`
- [ ] **helm 로컬 설치** — Windows: `winget install Helm.Helm`
- [ ] **SSH 클라이언트** — Git Bash (`C:\Program Files\Git\usr\bin\ssh.exe`) 또는 PowerShell `ssh`
- [ ] **GitHub repo write 권한** — 본 프로젝트
- [ ] **Docker Hub 계정** — `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` 보유
- [ ] **OpenAI API Key** — ai-service 용
- [ ] **Toss Payments 키** — payment-service 용 (기존 EC2 와 동일)
- [ ] **외부 Kafka 접근 가능** — 52.78.88.98:9092 (팀 공용)

검증 명령:
```bash
aws --version    # aws-cli/2.x
kubectl version --client    # Client Version: v1.30+
helm version    # version.BuildInfo{Version:"v3.x.x" ...}
ssh -V    # OpenSSH_x.x
```

---

## File Structure (이 작업에서 만들/수정할 모든 파일)

### Create (새 파일 — 모두 `k8s/` 디렉토리 안)

| 경로 | 책임 |
|---|---|
| `k8s/README.md` | K3s 운영 가이드 (kubectl 명령, 트러블슈팅) |
| `k8s/charts/microservice/Chart.yaml` | Helm 차트 메타데이터 |
| `k8s/charts/microservice/values.yaml` | 차트 기본값 (오버라이드 가능) |
| `k8s/charts/microservice/templates/_helpers.tpl` | 공통 라벨/이름 함수 |
| `k8s/charts/microservice/templates/deployment.yaml` | Pod 배포 정의 |
| `k8s/charts/microservice/templates/service.yaml` | ClusterIP Service |
| `k8s/charts/microservice/templates/ingress.yaml` | Traefik Ingress (gateway 만 활성) |
| `k8s/charts/microservice/templates/configmap.yaml` | 환경변수 (비밀 아닌 것) |
| `k8s/charts/microservice/templates/secret.yaml` | (선택) 차트 단위 Secret 생성 — 본 plan은 외부에서 생성 후 envFrom |
| `k8s/charts/microservice/templates/hpa.yaml` | HPA (선택, 노드 1대라 사실상 inactive) |
| `k8s/values/values-gateway.yaml` | gateway-service 명세 |
| `k8s/values/values-user.yaml` | user-service 명세 (review 통합) |
| `k8s/values/values-creator.yaml` | creator-service 명세 |
| `k8s/values/values-movie.yaml` | movie-service 명세 (ES OFF) |
| `k8s/values/values-payment.yaml` | payment-service 명세 |
| `k8s/values/values-ticket.yaml` | ticket-service 명세 |
| `k8s/values/values-settlement.yaml` | settlement-service 명세 |
| `k8s/values/values-streaming.yaml` | streaming-service 명세 (mem 1Gi) |
| `k8s/values/values-ai.yaml` | ai-service 명세 (mem 1Gi, OpenAI) |
| `k8s/infra/00-namespace.yaml` | `dev` 네임스페이스 |
| `k8s/infra/01-redis.sh` | Bitnami Redis Helm install 스크립트 |
| `k8s/infra/02-postgres.yaml` | in-cluster PostgreSQL Deployment + PVC + Service |
| `k8s/infra/03-secrets.sh` | K8s Secret 일괄 생성 (DB pw, JWT, Toss, S3, OpenAI 등) |
| `k8s/ops/pg-backup-cronjob.yaml` | 매일 02:00 pg_dump → S3 |
| `k8s/ops/logrotate.conf` | 호스트 EC2 의 /etc/logrotate.d/docker-containers |
| `k8s/aws/ec2-setup.md` | EC2 생성 절차 (콘솔 클릭 단위) |
| `k8s/aws/eip-attach.md` | Elastic IP 할당/연결 절차 |
| `k8s/aws/s3-bucket-policy.json` | S3 버킷 정책 (IAM User 접근) |
| `k8s/aws/iam-user-policy.json` | IAM User 에 부여할 S3 정책 |
| `k8s/aws/eksctl-cluster.yaml` | (사용 안 함, eksctl 은 EKS 전용. 본 plan은 K3s) |

### Modify (기존 파일 수정)
| 경로 | 변경 내용 |
|---|---|
| `.github/workflows/cd.yml` | `deploy-to-ec2` job → `deploy-to-k3s` 로 교체 |
| `gateway-service/src/main/resources/application-prod.yaml` | 라우팅 URI 환경변수 형식 조정 (`SERVICE_HOST` 단일 변수), `review-service` 라우팅 → `user-service` 로 통합 |
| `docs/k8s-team-summary.md` | EKS 가정 → K3s 로 갱신 (별도 task) |

### Delete-from-tracking (파일 자체는 유지, git 추적만 제거)
| 경로 | 이유 |
|---|---|
| `.claude/settings.local.json` | 이미 `.gitignore` 등록되어 있는데 과거 실수로 추적됨. 첫 K3s 커밋에 묻어서 정리 |

---

## Phase 0: 브랜치 생성 + 사전 정리

### Task 0.1: K3s 작업 브랜치 생성

> **💡 이게 뭐냐면**
> 새 기능을 만들 때마다 별도 브랜치(독립적인 작업 공간)를 만들어서 거기서 일하는 게 git 의 핵심. 다 끝나면 PR 로 dev/main 에 합칩니다. 지금부터 만드는 모든 파일은 이 브랜치에 쌓입니다.

**Files:** (없음 — git 명령만)

- [ ] **Step 1: dev/main 최신화 후 새 브랜치 생성**

```bash
cd /c/Users/y00h/Downloads/programmers/project/beadv5_5_3M_BE
git checkout dev/main
git pull origin dev/main
git checkout -b feature/k3s-deployment
```

기대 결과:
```
Switched to a new branch 'feature/k3s-deployment'
```

- [ ] **Step 2: 현재 브랜치 확인**

Run:
```bash
git branch --show-current
```

Expected output:
```
feature/k3s-deployment
```

### Task 0.2: `.claude/settings.local.json` 추적 제거

> **💡 이게 뭐냐면**
> `.claude/settings.local.json` 은 본인 PC 에서만 쓰는 설정 (Claude Code 권한 등). `.gitignore` 에 등록되어 있는데도 과거에 한 번 실수로 commit 되어서 git 이 계속 추적 중. 추적만 떼어내면 됨 (파일 자체는 안 지움).

**Files:**
- Modify (untrack only): `.claude/settings.local.json`

- [ ] **Step 1: 현재 추적 상태 확인**

Run:
```bash
git ls-files .claude/
```

Expected output (추적 중이면):
```
.claude/settings.local.json
```

만약 출력이 비어있다면 이미 정리된 상태 — Task 0.2 전체 skip.

- [ ] **Step 2: 추적 제거 (파일 보존)**

Run:
```bash
git rm --cached .claude/settings.local.json
```

Expected output:
```
rm '.claude/settings.local.json'
```

- [ ] **Step 3: 파일이 디스크에는 여전히 존재하는지 확인**

Run:
```bash
ls -la .claude/settings.local.json
```

Expected: 파일이 보임 (rm --cached 는 git 추적만 끊고 파일은 안 지움).

### Task 0.3: `k8s/` 디렉토리 골격 생성

> **💡 이게 뭐냐면**
> 앞으로 만들 모든 K3s 관련 파일이 들어갈 디렉토리. git 은 빈 디렉토리를 추적 못 해서 `.gitkeep` 빈 파일 1개를 넣어 placeholder 로 씁니다.

**Files:**
- Create: `k8s/.gitkeep`
- Create: `k8s/charts/.gitkeep`
- Create: `k8s/values/.gitkeep`
- Create: `k8s/infra/.gitkeep`
- Create: `k8s/ops/.gitkeep`
- Create: `k8s/aws/.gitkeep`

- [ ] **Step 1: 디렉토리 트리 + .gitkeep 일괄 생성**

Run:
```bash
mkdir -p k8s/charts/microservice/templates k8s/values k8s/infra k8s/ops k8s/aws
touch k8s/.gitkeep k8s/charts/.gitkeep k8s/values/.gitkeep k8s/infra/.gitkeep k8s/ops/.gitkeep k8s/aws/.gitkeep
```

- [ ] **Step 2: 트리 확인**

Run:
```bash
find k8s -type d
```

Expected output:
```
k8s
k8s/aws
k8s/charts
k8s/charts/microservice
k8s/charts/microservice/templates
k8s/infra
k8s/ops
k8s/values
```

### Task 0.4: 첫 커밋

> **💡 이게 뭐냐면**
> Task 0.1~0.3 까지 결과물(브랜치 + .claude 정리 + k8s 골격)을 하나의 commit 으로 묶어 git history 에 기록. 이후 작업이 망가져도 여기로 돌아올 수 있는 안전점.

- [ ] **Step 1: 변경 사항 확인**

Run:
```bash
git status
```

Expected:
```
On branch feature/k3s-deployment
Changes to be committed:
  deleted:    .claude/settings.local.json

Untracked files:
  k8s/.gitkeep
  k8s/aws/.gitkeep
  k8s/charts/.gitkeep
  k8s/infra/.gitkeep
  k8s/ops/.gitkeep
  k8s/values/.gitkeep
```

- [ ] **Step 2: 모든 변경 staging**

Run:
```bash
git add .claude/settings.local.json k8s/
```

- [ ] **Step 3: 커밋**

Run:
```bash
git commit -m "$(cat <<'EOF'
chore(k3s): k8s 디렉토리 초기화 + .claude 정리

- k8s/ 골격 디렉토리 생성 (charts, values, infra, ops, aws)
- .claude/settings.local.json git 추적 제거 (파일은 보존)
EOF
)"
```

Expected: commit 성공 메시지.

---

## Phase 1: AWS 인프라 셋업 (수동, 콘솔 작업 위주)

> **💡 이 Phase 는 코드가 아니라 AWS 콘솔 클릭 작업** 입니다. 각 step 마다 "어디 메뉴 → 어떤 버튼 → 어떤 값 입력" 식으로 적습니다. 본인이 직접 AWS 콘솔에 로그인해서 따라 하시면 됩니다.

### Task 1.1: AWS 콘솔에서 VPC + 보안그룹 구성

> **💡 이게 뭐냐면**
> - **VPC** = AWS 안에 본인만의 가상 네트워크 (마치 단지 부지). 운영 페이지에서 받은 자원에 "VPC 1개 생성 가능" 명시됨.
> - **보안 그룹(Security Group)** = 단지 정문 경비. 어떤 포트 / 어떤 IP 에서 들어오는 트래픽을 허용할지 결정.
> - 우리는 EC2 1대 띄울 거라 VPC 안에 Public Subnet 1개만 있으면 됩니다.

**Files:**
- Create: `k8s/aws/ec2-setup.md` (절차 문서)

- [ ] **Step 1: AWS 콘솔 로그인 + 리전 변경**

1. AWS 콘솔 접속
2. 우측 상단 리전 드롭다운에서 **`Asia Pacific (Seoul) ap-northeast-2`** 선택

- [ ] **Step 2: VPC 생성**

1. 검색창 → `VPC` 입력 → VPC 서비스 진입
2. 좌측 메뉴 `Your VPCs` → 우측 `Create VPC` 클릭
3. 입력값:
   - **Resources to create**: `VPC and more` 선택 (Subnet/IGW/Route Table 자동 생성)
   - **Name tag auto-generation**: `beadv5-k3s`
   - **IPv4 CIDR block**: `10.0.0.0/16` (기본)
   - **Number of Availability Zones (AZs)**: `1`
   - **Number of public subnets**: `1`
   - **Number of private subnets**: `0`
   - **NAT gateways**: `None`
   - **VPC endpoints**: `None`
4. 우측 하단 `Create VPC` 클릭
5. 약 1~2분 후 모두 ✓ 표시되면 완료

- [ ] **Step 3: 생성된 VPC 확인**

좌측 메뉴 `Your VPCs` → `beadv5-k3s-vpc` 한 행 확인.
좌측 메뉴 `Subnets` → `beadv5-k3s-subnet-public1-ap-northeast-2a` (이름 약간 다를 수 있음) 1개 확인.

- [ ] **Step 4: 보안그룹 생성**

1. 좌측 메뉴 `Security Groups` → `Create security group`
2. 입력값:
   - **Security group name**: `beadv5-k3s-sg`
   - **Description**: `K3s single node + SSH + HTTP/HTTPS + K3s API`
   - **VPC**: `beadv5-k3s-vpc` (위에서 만든 것)

3. **Inbound rules** (총 4개 추가):

| Type | Protocol | Port range | Source | Description |
|---|---|---|---|---|
| SSH | TCP | 22 | My IP (자동 인식) | 본인 PC SSH 배포 |
| HTTP | TCP | 80 | Anywhere-IPv4 (0.0.0.0/0) | 시연 트래픽 |
| HTTPS | TCP | 443 | Anywhere-IPv4 | (도메인 후 사용) |
| Custom TCP | TCP | 6443 | My IP | K3s API (kubectl 로컬 접속) |

4. **Outbound rules**: 기본값 (`All traffic, 0.0.0.0/0`) 유지 — Kafka/Docker Hub/S3 모두 통과

5. `Create security group` 클릭

- [ ] **Step 5: 절차 문서 저장**

Create file `k8s/aws/ec2-setup.md`:

```markdown
# EC2 + K3s 셋업 절차 (수동)

## 1. VPC
- 이름: beadv5-k3s
- CIDR: 10.0.0.0/16
- AZ: ap-northeast-2a
- Public Subnet 1개 (자동 생성)
- Internet Gateway 1개 (자동 생성)

## 2. Security Group: beadv5-k3s-sg
| Inbound | 22(SSH, MyIP) / 80(HTTP, 0.0.0.0/0) / 443(HTTPS, 0.0.0.0/0) / 6443(K3s API, MyIP) |
| Outbound | All (0.0.0.0/0) |

## 3. EC2 (Task 1.2)
- AMI: Ubuntu 22.04 LTS
- Type: t3.large
- EBS: 50 GB gp3
- Subnet: beadv5-k3s 의 public subnet
- Auto-assign public IP: Disable (EIP 따로 attach)

## 4. Elastic IP (Task 1.3)
- 1개 할당 → EC2 attach

## 5. K3s 설치 (Task 1.4)
- SSH 접속 → swap 8GB 추가 → curl ... | sh -

## 6. S3 + IAM (Task 1.5)
- 버킷: beadv5-uploads-{팀번호}
- IAM User: beadv5-s3-uploader (Access Key 발급)
```

- [ ] **Step 6: 디렉토리 확인 + commit**

Run:
```bash
ls k8s/aws/
git add k8s/aws/ec2-setup.md
git commit -m "docs(k3s): EC2 + K3s 셋업 절차 문서화"
```

### Task 1.2: EC2 t3.large 인스턴스 생성

> **💡 이게 뭐냐면**
> 우리가 K3s 를 올릴 가상 머신을 AWS 에서 켭니다. t3.large = 2 vCPU + 8GB RAM, EBS 50GB = 디스크. Ubuntu 22.04 LTS 를 OS 로 사용 (K3s 호환성 검증된 버전).

- [ ] **Step 1: EC2 콘솔 진입**

1. 검색창 → `EC2` → EC2 서비스 진입
2. 좌측 메뉴 `Instances` → `Launch instances`

- [ ] **Step 2: 인스턴스 입력값**

| 항목 | 값 |
|---|---|
| Name | `beadv5-k3s-node` |
| AMI | `Ubuntu Server 22.04 LTS` (HVM, SSD, x86_64) — Free tier eligible 표시 있음 |
| Instance type | `t3.large` |
| Key pair | (없으면 `Create new key pair` → 이름 `beadv5-k3s-key` → 타입 `RSA` → 형식 `.pem` → 다운로드. 이 파일 절대 분실 금지) |
| Network settings | `Edit` 클릭 |
| → VPC | `beadv5-k3s-vpc` |
| → Subnet | `beadv5-k3s-subnet-public1-ap-northeast-2a` |
| → Auto-assign public IP | `Disable` (Elastic IP 따로) |
| → Firewall (security groups) | `Select existing security group` → `beadv5-k3s-sg` |
| Configure storage | 8 GB → **`50 GB` 로 변경**, 타입 `gp3` 유지 |

- [ ] **Step 3: Launch + 검증**

1. 우측 하단 `Launch instance` 클릭
2. 약 1~2분 후 인스턴스 상태 `Running`, `Status check: 2/2 checks passed`
3. 인스턴스 행 클릭 → 하단 `Details` 탭 → **Public IPv4 address** = 비어있음 (정상, EIP 안 붙임)

- [ ] **Step 4: PEM 파일 권한 설정 (로컬)**

다운로드한 `beadv5-k3s-key.pem` 을 안전한 위치로 이동, 권한 600.

```bash
# 본인 홈 ~/.ssh/ 에 두는 걸 권장
mv ~/Downloads/beadv5-k3s-key.pem ~/.ssh/beadv5-k3s-key.pem
chmod 600 ~/.ssh/beadv5-k3s-key.pem
```

Windows Git Bash 에서 chmod 600 안 먹는 경우:
```bash
# PowerShell 에서:
icacls "$env:USERPROFILE\.ssh\beadv5-k3s-key.pem" /inheritance:r
icacls "$env:USERPROFILE\.ssh\beadv5-k3s-key.pem" /grant:r "$($env:USERNAME):(R)"
```

### Task 1.3: Elastic IP 할당 + EC2 attach

> **💡 이게 뭐냐면**
> EC2 의 기본 Public IP 는 "재시작하면 바뀝니다". 그러면 GitHub Actions / 도메인 / 본인 PC SSH 설정 다 깨짐. **Elastic IP** = 고정 공인 IP. 인스턴스에 attach 한 동안은 무료, detach 하면 시간당 ~$0.005 과금. 받은 자원 명세에 "EIP 인스턴스 별로 1개" 명시됨.

- [ ] **Step 1: EIP 할당**

1. EC2 콘솔 좌측 메뉴 `Elastic IPs` → `Allocate Elastic IP address`
2. 입력값:
   - Network Border Group: `ap-northeast-2`
   - Public IPv4 address pool: `Amazon's pool of IPv4 addresses`
3. `Allocate` 클릭

생성된 EIP 의 IPv4 주소 메모 (예: `13.125.xxx.xxx`).

- [ ] **Step 2: EC2 에 attach**

1. 방금 만든 EIP 행 선택 → `Actions` → `Associate Elastic IP address`
2. 입력값:
   - Resource type: `Instance`
   - Instance: `beadv5-k3s-node` 검색해서 선택
   - Private IP address: 자동 채워짐 (그대로 OK)
3. `Associate` 클릭

- [ ] **Step 3: SSH 접속 검증**

Run (본인 PC, EIP 주소를 본인 것으로 교체):
```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx
```

처음 접속 시 fingerprint 확인 메시지 → `yes` 입력.

Expected:
```
Welcome to Ubuntu 22.04.x LTS (GNU/Linux ...)
ubuntu@ip-10-0-x-x:~$
```

- [ ] **Step 4: EIP 주소를 GitHub Secrets 에 등록 (메모만, Phase 7 에서 사용)**

지금은 메모지에만 적어두기. Phase 7 의 Task 7.1 Step 2 에서 등록.

```
EC2_ELASTIC_IP = 13.125.xxx.xxx   # ← 본인 EIP 로 교체
```

### Task 1.4: EC2 안에서 swap 추가 + K3s 설치

> **💡 이게 뭐냐면**
> - **swap** = RAM 부족 시 디스크에 임시로 저장하는 공간. 8GB RAM 한계 때문에 8GB swap 을 미리 준비 (디스크에서 50GB 중 8GB 사용).
> - **K3s** = 경량 K8s. `curl ...sh` 한 줄로 설치 끝. 설치되면 systemd 서비스로 등록되어 부팅 시 자동 시작.

**Files:**
- (EC2 안에서만 작업, repo 파일 변경 없음)

- [ ] **Step 1: SSH 로 EC2 접속 (Task 1.3 Step 3 그대로)**

```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx
```

- [ ] **Step 2: 시스템 업데이트**

Run (EC2 안):
```bash
sudo apt update && sudo apt upgrade -y
```

- [ ] **Step 3: swap 8GB 추가**

Run (EC2 안):
```bash
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

검증:
```bash
free -h
```

Expected:
```
               total        used        free      shared  buff/cache   available
Mem:           7.7Gi       ...         ...         ...         ...         ...
Swap:          8.0Gi          0B       8.0Gi
```

- [ ] **Step 4: K3s 설치 (버전 고정)**

Run (EC2 안):
```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.30.3+k3s1 sh -
```

설치 약 30초~1분. Expected 마지막 출력:
```
[INFO]  systemd: Enabling k3s unit
Created symlink ...
[INFO]  systemd: Starting k3s
```

- [ ] **Step 5: K3s 노드 상태 검증**

Run (EC2 안):
```bash
sudo kubectl get nodes
```

Expected:
```
NAME              STATUS   ROLES                  AGE   VERSION
ip-10-0-x-x       Ready    control-plane,master   30s   v1.30.3+k3s1
```

- [ ] **Step 6: K3s 컴포넌트 확인**

Run (EC2 안):
```bash
sudo kubectl get pods -A
```

Expected: `kube-system` 네임스페이스에 `coredns`, `traefik`, `metrics-server`, `local-path-provisioner` 모두 `Running`.

만약 Pending 또는 ContainerCreating 상태면 1~2분 더 대기.

### Task 1.5: kubeconfig 를 본인 PC 로 복사

> **💡 이게 뭐냐면**
> `kubectl` 명령은 K3s API 와 통신해야 동작. 어떤 K3s, 어떤 사용자, 어떤 인증서로 접속할지 적힌 파일이 **kubeconfig**. EC2 안 `/etc/rancher/k3s/k3s.yaml` 에 있는 걸 본인 PC 로 가져옵니다.

**Files:**
- (본인 PC) `~/.kube/config-beadv5-k3s` (새로 생성)

- [ ] **Step 1: EC2 안 kubeconfig 보기**

Run (EC2 안):
```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```

전체 내용 복사 (마우스 드래그 → Ctrl+Insert 또는 우클릭 복사).

또는 Run (본인 PC, 한 줄로 가져오기):
```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx 'sudo cat /etc/rancher/k3s/k3s.yaml' > ~/.kube/config-beadv5-k3s
```

- [ ] **Step 2: 파일 내 server URL 교체**

`~/.kube/config-beadv5-k3s` 안에 `server: https://127.0.0.1:6443` → 본인 EIP 로 교체:

Run (본인 PC):
```bash
sed -i 's|https://127.0.0.1:6443|https://13.125.xxx.xxx:6443|' ~/.kube/config-beadv5-k3s
```

(또는 텍스트 에디터로 직접 수정)

- [ ] **Step 3: KUBECONFIG 환경변수 설정**

이번 셸 세션에만 적용:
```bash
export KUBECONFIG=~/.kube/config-beadv5-k3s
```

영구 적용 (Git Bash):
```bash
echo 'export KUBECONFIG=~/.kube/config-beadv5-k3s' >> ~/.bashrc
source ~/.bashrc
```

- [ ] **Step 4: 본인 PC 에서 K3s 접속 검증**

Run (본인 PC):
```bash
kubectl get nodes
```

Expected: EC2 의 노드 1개가 Ready 상태로 보임.

만약 connection refused: 보안 그룹 6443 포트 확인 (Task 1.1 Step 4).

### Task 1.6: S3 버킷 + IAM User 생성

> **💡 이게 뭐냐면**
> - **S3 버킷** = 영상/이미지 저장소. 받은 자원에 "S3 1개" 명시됨.
> - **IAM User** = AWS 안의 가짜 사용자. 이 사용자에게만 S3 버킷 접근 권한 부여 → Access Key 받아서 user-service / streaming-service 가 사용.
> - K3s 안에서 S3 호출할 때 이 Access Key 를 K8s Secret 으로 주입.

**Files:**
- Create: `k8s/aws/s3-bucket-policy.json`
- Create: `k8s/aws/iam-user-policy.json`

- [ ] **Step 1: S3 버킷 생성**

1. AWS 콘솔 검색 → `S3`
2. `Create bucket`
3. 입력값:
   - **Bucket name**: `beadv5-uploads-{팀번호}` (예: `beadv5-uploads-1`) — 글로벌 unique 필요
   - **Region**: `ap-northeast-2`
   - **Object Ownership**: `ACLs disabled` (기본)
   - **Block Public Access**: 모두 체크 유지 (기본). 영상은 presigned URL 로 제공할 거라 public 노출 불필요
   - **Bucket Versioning**: `Disable`
   - **Encryption**: `SSE-S3` (기본)
4. `Create bucket`

- [ ] **Step 2: IAM User 생성용 정책 파일 작성**

Create file `k8s/aws/iam-user-policy.json` (버킷 이름 본인 것으로 교체):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowS3UploadsBucket",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetObjectAcl",
        "s3:PutObjectAcl"
      ],
      "Resource": [
        "arn:aws:s3:::beadv5-uploads-1",
        "arn:aws:s3:::beadv5-uploads-1/*"
      ]
    }
  ]
}
```

- [ ] **Step 3: S3 버킷 정책 (선택, public read 가 필요한 경우만)**

Create file `k8s/aws/s3-bucket-policy.json` — **본 plan 은 일단 사용 안 함** (presigned URL 방식). 나중에 영상 직접 public 제공 결정 시 활용.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadOnlyForVideoStreaming",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::beadv5-uploads-1/streaming/*"
    }
  ]
}
```

- [ ] **Step 4: IAM User 생성 + 정책 attach**

1. AWS 콘솔 검색 → `IAM` → 좌측 `Users` → `Create user`
2. 입력값:
   - User name: `beadv5-s3-uploader`
   - Provide user access to AWS Management Console: 체크 해제 (프로그램 전용)
3. `Next`
4. **Set permissions** → `Attach policies directly` → 우측 `Create policy` 새 탭 열기
5. 새 탭에서:
   - JSON 탭 → 위 `iam-user-policy.json` 내용 붙여넣기
   - `Next`
   - Policy name: `beadv5-s3-uploader-policy`
   - `Create policy`
6. 원래 탭 돌아와서 `Refresh` → 방금 만든 `beadv5-s3-uploader-policy` 검색해서 체크
7. `Next` → `Create user`

- [ ] **Step 5: Access Key 발급**

1. 사용자 목록에서 `beadv5-s3-uploader` 클릭 → `Security credentials` 탭 → `Create access key`
2. Use case: `Application running outside AWS` (또는 `Other`)
3. `Next` → `Create access key`
4. **Access Key ID** + **Secret Access Key** 안전한 곳에 즉시 저장 (콘솔 닫으면 Secret 다시 못 봄)
5. `.csv` 다운로드 권장

메모:
```
AWS_ACCESS_KEY_ID     = AKIA...
AWS_SECRET_ACCESS_KEY = ...
S3_BUCKET             = beadv5-uploads-1
```

→ Phase 2 Task 2.4 에서 K8s Secret 으로 등록.

- [ ] **Step 6: 정책 파일 commit**

Run:
```bash
git add k8s/aws/s3-bucket-policy.json k8s/aws/iam-user-policy.json
git commit -m "feat(k3s): S3 버킷 + IAM User 정책 정의"
```

---

## Phase 2: K3s 인프라 컴포넌트 설치

> **💡 Phase 1 이 "AWS 안에 EC2 와 K3s 빈 클러스터를 마련"하는 단계였다면, Phase 2 는 "그 빈 K3s 안에 공용 시설(네임스페이스, DB, Redis, Secret 들)을 까는 단계"입니다. 한 번 깔면 다시 안 깝니다.

### Task 2.1: `dev` 네임스페이스 생성

> **💡 이게 뭐냐면**
> **네임스페이스** = K8s 안의 폴더 같은 것. 같은 클러스터에서도 dev / prod / monitoring 등 분리해서 관리. 우리는 일단 `dev` 만 사용.

**Files:**
- Create: `k8s/infra/00-namespace.yaml`

- [ ] **Step 1: 네임스페이스 매니페스트 작성**

Create file `k8s/infra/00-namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: dev
  labels:
    name: dev
    env: development
```

- [ ] **Step 2: 적용**

Run (본인 PC):
```bash
kubectl apply -f k8s/infra/00-namespace.yaml
```

Expected:
```
namespace/dev created
```

- [ ] **Step 3: 검증**

Run:
```bash
kubectl get namespaces
```

Expected: `dev` 가 목록에 보임 (`default`, `kube-system`, `kube-public`, `kube-node-lease`, `dev`).

- [ ] **Step 4: commit**

```bash
git add k8s/infra/00-namespace.yaml
git commit -m "feat(k3s): dev namespace 생성"
```

### Task 2.2: in-cluster PostgreSQL 배포 (Deployment + PVC + Service)

> **💡 이게 뭐냐면**
> - **Deployment** = "Postgres Pod 1개를 항상 띄워둬!" 명령서.
> - **PVC (Persistent Volume Claim)** = "EBS 디스크 10GB 빌려줘!" 요청서. K3s 의 local-path-provisioner 가 자동으로 호스트 EC2 디스크 일부 (`/var/lib/rancher/k3s/storage/`) 를 마운트해줍니다.
> - **Service** = "다른 Pod들이 `postgres.dev.svc.cluster.local:5432` 로 접근할 수 있게 안내판 세워!"

**Files:**
- Create: `k8s/infra/02-postgres.yaml`

- [ ] **Step 1: Postgres 매니페스트 작성**

Create file `k8s/infra/02-postgres.yaml`:

```yaml
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: dev
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: local-path
  resources:
    requests:
      storage: 10Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: dev
  labels:
    app: postgres
spec:
  replicas: 1
  strategy:
    type: Recreate    # PVC 재마운트 안전을 위해 RollingUpdate 대신
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 5432
              name: postgres
          env:
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: POSTGRES_PASSWORD
            - name: POSTGRES_DB
              value: "postgres"
            - name: PGDATA
              value: "/var/lib/postgresql/data/pgdata"
          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          volumeMounts:
            - name: postgres-storage
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 10
            periodSeconds: 5
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: postgres-storage
          persistentVolumeClaim:
            claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: dev
  labels:
    app: postgres
spec:
  type: ClusterIP
  ports:
    - port: 5432
      targetPort: 5432
      name: postgres
  selector:
    app: postgres
```

- [ ] **Step 2: postgres-secret 먼저 만들기 (위 매니페스트가 참조)**

Run (본인 PC, 비밀번호는 강한 것으로 교체):
```bash
kubectl create secret generic postgres-secret \
  --namespace=dev \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD='S0meStr0ng!Pw_2026'
```

Expected:
```
secret/postgres-secret created
```

(나중에 Task 2.4 에서 더 정리합니다. 일단 Postgres 띄우려면 이 secret 이 먼저 필요함)

- [ ] **Step 3: Postgres 적용**

Run:
```bash
kubectl apply -f k8s/infra/02-postgres.yaml
```

Expected:
```
persistentvolumeclaim/postgres-pvc created
deployment.apps/postgres created
service/postgres created
```

- [ ] **Step 4: Postgres Pod 검증 (1~2분 소요)**

Run:
```bash
kubectl get pod -n dev -l app=postgres -w
```

Expected (몇 초~1분 후):
```
NAME                        READY   STATUS    RESTARTS   AGE
postgres-7d5...-xxxxx       1/1     Running   0          30s
```

`Ctrl+C` 로 watch 종료.

- [ ] **Step 5: Postgres 안에 8개 DB 스키마 생성**

Run:
```bash
kubectl exec -n dev -it deploy/postgres -- psql -U postgres -c "
CREATE DATABASE creator_db;
CREATE DATABASE user_db;
CREATE DATABASE movie_db;
CREATE DATABASE payment_db;
CREATE DATABASE ticket_db;
CREATE DATABASE settlement_db;
CREATE DATABASE streaming_db;
CREATE DATABASE ai_db;
"
```

Expected:
```
CREATE DATABASE
CREATE DATABASE
... (8번)
```

- [ ] **Step 6: ai_db 에 pgvector 익스텐션 활성화 (담당 조원이 사용 시)**

Run:
```bash
kubectl exec -n dev -it deploy/postgres -- psql -U postgres -d ai_db -c "
CREATE EXTENSION IF NOT EXISTS vector;
"
```

만약 `extension "vector" is not available` 에러:
- `postgres:16-alpine` 이미지엔 pgvector 없음
- 02-postgres.yaml 의 `image:` 를 `pgvector/pgvector:pg16` 로 교체 후 재배포

조원이 pgvector 안 쓰면 이 step skip.

- [ ] **Step 7: DB 목록 확인**

Run:
```bash
kubectl exec -n dev -it deploy/postgres -- psql -U postgres -c "\l"
```

Expected: 위에서 만든 8개 DB 가 모두 보임.

- [ ] **Step 8: commit**

```bash
git add k8s/infra/02-postgres.yaml
git commit -m "feat(k3s): in-cluster PostgreSQL 16 + 8 DBs (PVC 10Gi)"
```

### Task 2.3: Redis (Bitnami Helm) 설치

> **💡 이게 뭐냐면**
> Redis 는 Bitnami(VMware 자회사) 가 만든 표준 Helm 차트가 가장 안정적. 우리는 `helm install` 명령 하나로 설치 끝. master 1개만 띄우고 (replication 없이), 메모리 ~150MB.

**Files:**
- Create: `k8s/infra/01-redis.sh`

- [ ] **Step 1: Helm repo 등록 (1회)**

Run (본인 PC):
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

Expected:
```
"bitnami" has been added to your repositories
Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "bitnami" chart repository
Update Complete. ⎈Happy Helming!⎈
```

- [ ] **Step 2: Redis 설치 스크립트 작성**

Create file `k8s/infra/01-redis.sh`:

```bash
#!/usr/bin/env bash
# Bitnami Redis 설치 스크립트 (단일 master, replica 없음, 인증 없음 — 클러스터 내부 전용)
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

echo "Redis 설치 완료. 접속 endpoint: redis-master.dev.svc.cluster.local:6379"
```

- [ ] **Step 3: 실행 권한 + 실행**

Run (본인 PC):
```bash
chmod +x k8s/infra/01-redis.sh
./k8s/infra/01-redis.sh
```

Expected (1~3분 소요):
```
Release "redis" does not exist. Installing it now.
NAME: redis
LAST DEPLOYED: ...
NAMESPACE: dev
STATUS: deployed
...
Redis 설치 완료. 접속 endpoint: redis-master.dev.svc.cluster.local:6379
```

- [ ] **Step 4: 검증**

Run:
```bash
kubectl get pod -n dev -l app.kubernetes.io/name=redis
```

Expected:
```
NAME             READY   STATUS    RESTARTS   AGE
redis-master-0   1/1     Running   0          1m
```

- [ ] **Step 5: 연결 테스트**

Run:
```bash
kubectl run redis-test --rm -it --image=redis:7-alpine -n dev --restart=Never -- redis-cli -h redis-master.dev.svc.cluster.local PING
```

Expected:
```
PONG
```

(Pod 자동 삭제됨)

- [ ] **Step 6: commit**

```bash
git add k8s/infra/01-redis.sh
git commit -m "feat(k3s): Bitnami Redis standalone (auth off, in-cluster)"
```

### Task 2.4: K8s Secret 일괄 생성 스크립트

> **💡 이게 뭐냐면**
> K8s 안에서 비밀번호/토큰 같은 거를 보관하는 게 **Secret**. 평문이 아닌 base64 인코딩으로 저장 (암호화 X — 단지 git 에 평문 노출 방지). 9개 서비스가 사용할 모든 비밀을 한 스크립트로 등록.

**Files:**
- Create: `k8s/infra/03-secrets.sh`
- Create: `k8s/infra/.env.k3s.example` (값은 빈 채로 — 본인 PC 에서 채워서 `.env.k3s` 로 복사 후 사용)

- [ ] **Step 1: .env 예제 파일 작성**

Create file `k8s/infra/.env.k3s.example`:

```bash
# K3s Secret 등록용 환경변수 예제.
# 이 파일을 .env.k3s 로 복사 후 실제 값으로 채우고, .env.k3s 는 git 에 절대 commit 하지 말 것.
# (.gitignore 에 이미 *.env 등록되어 있으면 안전)

# Postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=ChangeMe_StrongPw_2026

# JWT (RSA 키쌍, docker-compose .env 와 동일한 이름)
JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----"
JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\nMIIB...\n-----END PUBLIC KEY-----"
JWT_TOKEN_PUBLIC="-----BEGIN PUBLIC KEY-----\nMIIB...\n-----END PUBLIC KEY-----"   # gateway 에서 검증용 (PUBLIC 과 동일 가능)
JWT_ACCESS_TOKEN_EXPIRY=3600
JWT_REFRESH_TOKEN_EXPIRY=2592000
STREAMING_JWT_SECRET=ChangeMe_StreamingJwtSecret_AtLeast32Chars

# Toss Payments (docker-compose 와 동일 변수명)
TOSS_PAYMENT_CK=test_ck_xxxxxxxxxxxx
TOSS_PAYMENT_SECRET=test_sk_xxxxxxxxxxxx

# AWS S3 (Task 1.6 에서 발급, docker-compose .env 와 동일 변수명)
AWS_ACCESS_KEY=AKIAxxxxxxxxxxxxxxxx
AWS_SECRET_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=beadv5-uploads-1

# OpenAI (ai-service)
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Kafka (외부 EC2)
KAFKA_BOOTSTRAP_SERVERS=52.78.88.98:9092
```

- [ ] **Step 2: .gitignore 에 .env.k3s 등록 (이미 있으면 skip)**

Run:
```bash
grep -q "k8s/infra/.env.k3s$" .gitignore || echo "k8s/infra/.env.k3s" >> .gitignore
```

- [ ] **Step 3: Secret 생성 스크립트 작성**

Create file `k8s/infra/03-secrets.sh`:

```bash
#!/usr/bin/env bash
# K3s 클러스터 dev 네임스페이스에 9개 서비스용 Secret 일괄 생성.
# 사용법:
#   1. .env.k3s.example 을 .env.k3s 로 복사 후 실제 값 채우기
#   2. ./k8s/infra/03-secrets.sh
set -euo pipefail

ENV_FILE="${ENV_FILE:-k8s/infra/.env.k3s}"
NAMESPACE="${NAMESPACE:-dev}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE 가 없습니다. .env.k3s.example 을 복사 후 값 채우세요."
  exit 1
fi

# .env 로드
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "[1/5] postgres-secret (DB 접속 정보)"
kubectl create secret generic postgres-secret \
  --namespace="$NAMESPACE" \
  --from-literal=POSTGRES_USER="$POSTGRES_USER" \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=DB_USERNAME="$POSTGRES_USER" \
  --from-literal=DB_PASSWORD="$POSTGRES_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[2/5] jwt-secret (JWT 발급/검증 키 — RSA 키쌍, docker-compose 와 동일 변수명)"
kubectl create secret generic jwt-secret \
  --namespace="$NAMESPACE" \
  --from-literal=JWT_PRIVATE_KEY="$JWT_PRIVATE_KEY" \
  --from-literal=JWT_PUBLIC_KEY="$JWT_PUBLIC_KEY" \
  --from-literal=JWT_TOKEN_PUBLIC="$JWT_TOKEN_PUBLIC" \
  --from-literal=JWT_ACCESS_TOKEN_EXPIRY="$JWT_ACCESS_TOKEN_EXPIRY" \
  --from-literal=JWT_REFRESH_TOKEN_EXPIRY="$JWT_REFRESH_TOKEN_EXPIRY" \
  --from-literal=STREAMING_JWT_SECRET="$STREAMING_JWT_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[3/5] toss-secret (Toss Payments)"
kubectl create secret generic toss-secret \
  --namespace="$NAMESPACE" \
  --from-literal=TOSS_PAYMENT_CK="$TOSS_PAYMENT_CK" \
  --from-literal=TOSS_PAYMENT_SECRET="$TOSS_PAYMENT_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[4/5] aws-secret (S3 + IAM, docker-compose 와 동일 변수명)"
kubectl create secret generic aws-secret \
  --namespace="$NAMESPACE" \
  --from-literal=AWS_ACCESS_KEY="$AWS_ACCESS_KEY" \
  --from-literal=AWS_SECRET_KEY="$AWS_SECRET_KEY" \
  --from-literal=AWS_REGION="$AWS_REGION" \
  --from-literal=AWS_S3_BUCKET="$AWS_S3_BUCKET" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[5/5] openai-secret (ai-service)"
kubectl create secret generic openai-secret \
  --namespace="$NAMESPACE" \
  --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "✅ 모든 Secret 등록 완료. 확인: kubectl get secret -n $NAMESPACE"
```

- [ ] **Step 4: .env.k3s 채우고 실행**

Run (본인 PC):
```bash
cp k8s/infra/.env.k3s.example k8s/infra/.env.k3s
# 텍스트 에디터로 k8s/infra/.env.k3s 열어서 실제 값 채우기
chmod +x k8s/infra/03-secrets.sh
./k8s/infra/03-secrets.sh
```

Expected:
```
[1/5] postgres-secret (DB 접속 정보)
secret/postgres-secret configured
[2/5] jwt-secret ...
secret/jwt-secret created
[3/5] toss-secret ...
secret/toss-secret created
[4/5] aws-secret ...
secret/aws-secret created
[5/5] openai-secret ...
secret/openai-secret created

✅ 모든 Secret 등록 완료. ...
```

- [ ] **Step 5: Secret 목록 검증**

Run:
```bash
kubectl get secret -n dev
```

Expected: 5개 Secret 보임 (`aws-secret`, `jwt-secret`, `openai-secret`, `postgres-secret`, `toss-secret`).

- [ ] **Step 6: commit (스크립트 + 예제만, .env.k3s 는 절대 commit X)**

```bash
git status   # .env.k3s 가 보이지 않는지 확인
git add k8s/infra/03-secrets.sh k8s/infra/.env.k3s.example .gitignore
git commit -m "feat(k3s): 9개 서비스용 Secret 일괄 생성 스크립트"
```

### Task 2.5: Phase 2 마무리 검증

> **💡 이게 뭐냐면**
> Phase 2 가 끝났으니 K3s 안의 인프라가 전부 정상인지 한 번에 확인.

**Files:** (없음)

- [ ] **Step 1: 네임스페이스 + 모든 리소스 한눈 보기**

Run:
```bash
kubectl get all,pvc,secret,configmap -n dev
```

Expected (요약):
- Pods: `postgres-xxx Running`, `redis-master-0 Running`
- Services: `postgres`, `redis-master`, `redis-headless`
- Deployments: `postgres`
- StatefulSets: `redis-master`
- PVCs: `postgres-pvc`, `redis-data-redis-master-0` 모두 `Bound`
- Secrets: 5개

- [ ] **Step 2: 메모리 사용량 확인 (인프라만)**

Run:
```bash
kubectl top pod -n dev
```

Expected: postgres ~150MB, redis ~30MB. 합계 ~200MB.

(만약 `metrics-server not ready` 에러: K3s 시작 직후라면 1~2분 대기)

- [ ] **Step 3: 노드 가용 메모리 체크**

Run:
```bash
kubectl describe node | grep -A 5 "Allocated resources"
```

Expected: memory requests ~500MB / 7.7GB, limits ~2GB. **남은 가용 메모리 약 7GB → 9개 서비스 들어갈 자리 확보**.

---

## Phase 3: Helm 차트 작성 (`charts/microservice/`)

> **💡 Phase 3 가 이번 작업의 가장 큰 산** 입니다. 한 번 만들어 두면 9개 서비스가 다 같은 차트 + 다른 values 로 배포되니, 매니페스트 중복이 사라집니다.
>
> **Helm 차트 = K8s 매니페스트 템플릿 엔진**. `{{ .Values.image.repository }}` 같은 빈칸을 `values.yaml` 또는 `values-movie.yaml` 의 값으로 채워서 최종 K8s YAML 을 생성합니다.

### Task 3.1: Chart.yaml + values.yaml (기본값)

> **💡 이게 뭐냐면**
> - **Chart.yaml** = 차트 메타데이터 (이름, 버전, 설명).
> - **values.yaml** = 모든 빈칸의 기본값. `values-{서비스}.yaml` 에서 일부만 오버라이드합니다.

**Files:**
- Create: `k8s/charts/microservice/Chart.yaml`
- Create: `k8s/charts/microservice/values.yaml`

- [ ] **Step 1: Chart.yaml 작성**

Create file `k8s/charts/microservice/Chart.yaml`:

```yaml
apiVersion: v2
name: microservice
description: beadv5 프로젝트 9개 마이크로서비스 공통 Helm 차트 (K3s on EC2)
type: application
version: 0.1.0
appVersion: "1.0.0"
keywords:
  - spring-boot
  - microservice
  - k3s
maintainers:
  - name: y0000h2
```

- [ ] **Step 2: values.yaml 작성 (기본값 — 일반 7개 서비스 기준)**

Create file `k8s/charts/microservice/values.yaml`:

```yaml
# beadv5 microservice 차트 기본값.
# 각 서비스는 values/values-{서비스명}.yaml 에서 일부만 오버라이드합니다.

# ──────────────────────────────────────────
# 이름
# ──────────────────────────────────────────
nameOverride: ""        # 비워두면 release 이름 사용
fullnameOverride: ""

# ──────────────────────────────────────────
# 이미지
# ──────────────────────────────────────────
image:
  repository: y0000h/CHANGE_ME_IN_VALUES   # 반드시 values-*.yaml 에서 오버라이드
  tag: latest
  pullPolicy: IfNotPresent

# ──────────────────────────────────────────
# Pod
# ──────────────────────────────────────────
replicaCount: 1   # 단일 노드라 사실상 1만 사용 (HA 불가)

podAnnotations: {}
podLabels: {}

securityContext:
  runAsNonRoot: false   # Spring Boot 이미지가 root 가정인 경우가 많음. 필요 시 true 로

# ──────────────────────────────────────────
# 컨테이너 포트 / Service
# ──────────────────────────────────────────
service:
  type: ClusterIP
  port: 8080            # K8s Service 노출 포트
  targetPort: 8080      # 컨테이너 내부 포트 (각 서비스에서 오버라이드)
  protocol: TCP

# ──────────────────────────────────────────
# 환경변수 (비밀 아닌 것)
# ──────────────────────────────────────────
env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"

# 클러스터 내부 의존성 (모든 서비스 공통 — 환경변수로 노출)
sharedEnv:
  KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"   # 외부 Kafka
  REDIS_HOST: "redis-master.dev.svc.cluster.local"
  REDIS_PORT: "6379"
  DB_HOST: "postgres.dev.svc.cluster.local"
  DB_PORT: "5432"

# ──────────────────────────────────────────
# Secret 참조 (envFrom)
# values-*.yaml 에서 enabled true/false 로 선택
# ──────────────────────────────────────────
secretRefs:
  postgres: true       # 모든 서비스 (DB 사용 안 하는 gateway 도 일단 true)
  jwt: true            # 인증 사용 서비스 모두
  toss: false          # payment 만 true
  aws: false           # user, streaming 만 true
  openai: false        # ai 만 true

# ──────────────────────────────────────────
# 리소스 (일반 7개 서비스 기본값)
# ──────────────────────────────────────────
resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 512Mi

# ──────────────────────────────────────────
# Liveness / Readiness Probe (Spring Boot Actuator 가정)
# ──────────────────────────────────────────
healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90       # Spring Boot 기동 시간
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

# ──────────────────────────────────────────
# Ingress (gateway 만 enabled)
# ──────────────────────────────────────────
ingress:
  enabled: false
  className: traefik
  annotations: {}
  hosts:
    - host: ""           # 도메인 없으면 빈 문자열 → IP 직접 매칭
      paths:
        - path: /
          pathType: Prefix
  tls: []                # 도메인 결정 후 cert-manager 통해 추가

# ──────────────────────────────────────────
# HPA (선택, 노드 1대라 사실상 inactive)
# ──────────────────────────────────────────
autoscaling:
  enabled: false
  minReplicas: 1
  maxReplicas: 2
  targetCPUUtilizationPercentage: 80
```

### Task 3.2: `_helpers.tpl` (공통 함수)

> **💡 이게 뭐냐면**
> Helm 의 함수 모음. `{{ include "microservice.fullname" . }}` 같이 호출해서 일관된 이름/라벨을 생성합니다. Bitnami 등 표준 차트와 같은 패턴.

**Files:**
- Create: `k8s/charts/microservice/templates/_helpers.tpl`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/_helpers.tpl`:

```
{{/*
Expand the name of the chart.
*/}}
{{- define "microservice.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
release name 우선, fullnameOverride 있으면 그걸 사용.
*/}}
{{- define "microservice.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "microservice.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{ include "microservice.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels (Pod ↔ Deployment ↔ Service 매칭에 사용)
*/}}
{{- define "microservice.selectorLabels" -}}
app.kubernetes.io/name: {{ include "microservice.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
```

### Task 3.3: ConfigMap 템플릿

> **💡 이게 뭐냐면**
> **ConfigMap** = 환경변수 같은 비밀 아닌 설정값을 K8s 안에 보관. Pod 가 envFrom 으로 통째로 import 가능. 각 서비스마다 ConfigMap 1개.

**Files:**
- Create: `k8s/charts/microservice/templates/configmap.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "microservice.fullname" . }}-env
  labels:
    {{- include "microservice.labels" . | nindent 4 }}
data:
  {{- range $key, $value := .Values.env }}
  {{ $key }}: {{ $value | quote }}
  {{- end }}
  {{- range $key, $value := .Values.sharedEnv }}
  {{ $key }}: {{ $value | quote }}
  {{- end }}
```

### Task 3.4: Deployment 템플릿

> **💡 이게 뭐냐면**
> Pod 를 어떻게 띄울지 적힌 핵심 매니페스트. 9개 서비스 모두 이 템플릿 한 장에서 생성됩니다.
>
> 핵심 포인트:
> - `envFrom`: ConfigMap (env) + 선택된 Secret 들을 통째로 환경변수로 import
> - `resources.limits`: OOM 보호
> - `livenessProbe`/`readinessProbe`: 자동 헬스체크 (Spring Boot Actuator)
> - `imagePullPolicy: IfNotPresent`: 같은 태그 이미지면 pull 안 함 (속도)

**Files:**
- Create: `k8s/charts/microservice/templates/deployment.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "microservice.fullname" . }}
  labels:
    {{- include "microservice.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 0
      maxUnavailable: 1   # 노드 1대라 maxSurge 0 (Pod 2개 띄울 메모리 없음)
  selector:
    matchLabels:
      {{- include "microservice.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "microservice.selectorLabels" . | nindent 8 }}
        {{- with .Values.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: {{ .Values.service.protocol }}
          envFrom:
            - configMapRef:
                name: {{ include "microservice.fullname" . }}-env
            {{- if .Values.secretRefs.postgres }}
            - secretRef:
                name: postgres-secret
            {{- end }}
            {{- if .Values.secretRefs.jwt }}
            - secretRef:
                name: jwt-secret
            {{- end }}
            {{- if .Values.secretRefs.toss }}
            - secretRef:
                name: toss-secret
            {{- end }}
            {{- if .Values.secretRefs.aws }}
            - secretRef:
                name: aws-secret
            {{- end }}
            {{- if .Values.secretRefs.openai }}
            - secretRef:
                name: openai-secret
            {{- end }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          {{- if .Values.healthcheck.enabled }}
          livenessProbe:
            httpGet:
              path: {{ .Values.healthcheck.path }}
              port: http
            initialDelaySeconds: {{ .Values.healthcheck.livenessInitialDelay }}
            periodSeconds: {{ .Values.healthcheck.livenessPeriod }}
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: {{ .Values.healthcheck.path }}
              port: http
            initialDelaySeconds: {{ .Values.healthcheck.readinessInitialDelay }}
            periodSeconds: {{ .Values.healthcheck.readinessPeriod }}
            failureThreshold: 3
          {{- end }}
      {{- with .Values.securityContext }}
      securityContext:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

### Task 3.5: Service 템플릿

> **💡 이게 뭐냐면**
> Pod 들에게 고정 DNS 이름 (`gateway.dev.svc.cluster.local:8080`)을 부여. Pod 가 죽고 새로 만들어져도 Service 이름은 그대로.

**Files:**
- Create: `k8s/charts/microservice/templates/service.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "microservice.fullname" . }}
  labels:
    {{- include "microservice.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: {{ .Values.service.protocol }}
      name: http
  selector:
    {{- include "microservice.selectorLabels" . | nindent 4 }}
```

### Task 3.6: Ingress 템플릿 (Traefik, gateway 만)

> **💡 이게 뭐냐면**
> 외부 트래픽이 들어왔을 때 어느 Service 로 보낼지. K3s 내장 Traefik 이 이걸 보고 자동 라우팅. `ingress.enabled: true` 인 차트(우리는 gateway 만)에서만 생성됩니다.

**Files:**
- Create: `k8s/charts/microservice/templates/ingress.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/ingress.yaml`:

```yaml
{{- if .Values.ingress.enabled -}}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "microservice.fullname" . }}
  labels:
    {{- include "microservice.labels" . | nindent 4 }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  {{- if .Values.ingress.tls }}
  tls:
    {{- range .Values.ingress.tls }}
    - hosts:
        {{- range .hosts }}
        - {{ . | quote }}
        {{- end }}
      secretName: {{ .secretName }}
    {{- end }}
  {{- end }}
  rules:
    {{- range .Values.ingress.hosts }}
    - {{- if .host }}
      host: {{ .host | quote }}
      {{- end }}
      http:
        paths:
          {{- range .paths }}
          - path: {{ .path }}
            pathType: {{ .pathType }}
            backend:
              service:
                name: {{ include "microservice.fullname" $ }}
                port:
                  number: {{ $.Values.service.port }}
          {{- end }}
    {{- end }}
{{- end }}
```

### Task 3.7: HPA 템플릿 (선택, inactive)

> **💡 이게 뭐냐면**
> **HPA (Horizontal Pod Autoscaler)** = CPU/메모리 사용률에 따라 Pod 개수 자동 증감. 우리는 노드 1대라 실질적으로 못 늘리지만, 향후 멀티노드 확장 대비해 템플릿만 준비. `autoscaling.enabled: false` 가 기본값이라 실제 생성 안 됨.

**Files:**
- Create: `k8s/charts/microservice/templates/hpa.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/charts/microservice/templates/hpa.yaml`:

```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "microservice.fullname" . }}
  labels:
    {{- include "microservice.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "microservice.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
{{- end }}
```

### Task 3.8: Helm 차트 syntax 검증 (실제 배포 X)

> **💡 이게 뭐냐면**
> `helm template` = 매니페스트를 실제로 K8s 에 보내지 않고 "최종 YAML 만 화면에 출력". 빈칸이 다 채워졌는지 확인용.
> `helm lint` = 차트의 문법 오류/누락 검사.

**Files:** (없음, 차트 검증만)

- [ ] **Step 1: helm lint**

Run (본인 PC, 프로젝트 루트):
```bash
helm lint k8s/charts/microservice
```

Expected:
```
==> Linting k8s/charts/microservice
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed
```

(`icon is recommended` 경고는 무시 OK)

- [ ] **Step 2: helm template — 임시 values 로 렌더링 확인**

Run:
```bash
helm template test-render k8s/charts/microservice \
  --set image.repository=y0000h/movie-service \
  --set image.tag=test \
  --set service.targetPort=8086 \
  --set ingress.enabled=true | head -100
```

Expected: ConfigMap, Deployment, Service, Ingress 4개 매니페스트가 화면에 출력. `{{ ... }}` 같은 빈칸이 하나도 없어야 함.

만약 `{{ ... }}` 가 남아있으면 templates 또는 _helpers.tpl 의 변수 이름 오타 가능성 — 검색해서 수정.

- [ ] **Step 3: commit**

```bash
git add k8s/charts/microservice/
git commit -m "feat(k3s): microservice Helm chart (templates 6개 + values 기본값)"
```

---

## Phase 4: values 파일 9개 작성 (서비스별 명세서)

> **💡 Phase 3 가 "도면" 만들기였다면 Phase 4 는 "9개 호수의 인테리어 명세서" 만들기. 각 파일이 한 서비스의 모든 다른점(이미지, 포트, 환경변수, 메모리)을 담습니다.

### Task 4.1: values-creator.yaml

> **💡 시작은 가장 단순한 서비스부터.** creator-service 는 의존이 적고 표준 패턴.

**Files:**
- Create: `k8s/values/values-creator.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-creator.yaml`:

```yaml
# creator-service (포트 8080)
# - 영화/제작사 정보 관리
# - DB: creator_db
# - Redis 캐싱 사용
# - JWT 인증 사용

image:
  repository: y0000h/creator-service   # ${DOCKERHUB_USERNAME} 본인 계정명으로 교체
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8080
  targetPort: 8080

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8080"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/creator_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"   # postgres-secret 에서 envFrom 으로 덮어씀
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  SPRING_DATA_REDIS_HOST: "redis-master.dev.svc.cluster.local"
  SPRING_DATA_REDIS_PORT: "6379"
  DDL_AUTO: "validate"
  FILE_UPLOAD_BASE_PATH: "/tmp/uploads"     # K3s 환경에서는 임시. 영구 저장은 S3
  SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: "500MB"
  SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: "500MB"

sharedEnv: {}    # base values.yaml 의 sharedEnv 를 위 env 로 흡수했으므로 비움

secretRefs:
  postgres: true
  jwt: true       # JWT_PRIVATE_KEY, JWT_PUBLIC_KEY 등 자동 주입
  toss: false
  aws: false
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 640Mi    # docker-compose mem_limit 와 동일

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.2: values-user.yaml (review 통합)

> **💡 user-service 는 docker-compose 에서 review 와 통합되어 가장 큰 서비스.** AWS S3, Google OAuth, Mail, MinIO 등 변수 많음.

**Files:**
- Create: `k8s/values/values-user.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-user.yaml`:

```yaml
# user-service (포트 8085) — review-service 통합
# - 사용자 관리, JWT 발급, S3 프로필 이미지, OAuth, Mail
# - DB: user_db

image:
  repository: y0000h/user-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8085
  targetPort: 8085

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8085"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/user_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  SPRING_DATA_REDIS_HOST: "redis-master.dev.svc.cluster.local"
  SPRING_DATA_REDIS_PORT: "6379"
  DDL_AUTO: "validate"
  # MinIO (사용 안 하면 빈 값 OK — S3 로 통일)
  MINIO_ENDPOINT: ""
  MINIO_ACCESS_KEY: ""
  MINIO_SECRET_KEY: ""
  MINIO_BUCKET: ""
  # Google OAuth (실제 값은 별도 google-secret 으로 분리 권장 — 일단 평문 placeholder)
  GOOGLE_CLIENT_ID: "REPLACE_WITH_REAL_OR_MOVE_TO_SECRET"
  GOOGLE_CLIENT_SECRET: "REPLACE_WITH_REAL_OR_MOVE_TO_SECRET"
  GOOGLE_REDIRECT_URI: "https://REPLACE_DOMAIN/login/oauth2/code/google"
  # Mail
  MAIL_USERNAME: "REPLACE_WITH_REAL"
  MAIL_PASSWORD: "REPLACE_WITH_REAL_OR_MOVE_TO_SECRET"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: true       # JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, ACCESS/REFRESH expiry
  toss: false
  aws: true       # AWS_ACCESS_KEY, AWS_SECRET_KEY, AWS_REGION, AWS_S3_BUCKET
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 640Mi

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 120     # OAuth/Mail 초기화 시간 더 길어서 +30s
  livenessPeriod: 30
  readinessInitialDelay: 60
  readinessPeriod: 10

ingress:
  enabled: false
```

> **⚠️ 주의:** Google OAuth Client Secret, Mail Password 같은 값들은 사실 git 에 commit 하면 안 됨. 본 plan 에서는 placeholder 로 두고, 실제 운영 시에는 별도 `google-secret`, `mail-secret` 만들어서 secretRefs 추가하는 걸 권장. 일단 시연 단계에서는 평문 placeholder 가 빠르므로 plan 그대로 진행.

### Task 4.3: values-movie.yaml (ES OFF, JPA 폴백)

**Files:**
- Create: `k8s/values/values-movie.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-movie.yaml`:

```yaml
# movie-service (포트 8086) — Elasticsearch OFF, JPA 폴백 (PR #133)
# - DB: docker-compose 기준 creator_db (movie_db 가 아님 — 확인 필요)
# - 만약 movie_db 사용이 맞다면 DATASOURCE_URL 을 movie_db 로 수정

image:
  repository: y0000h/movie-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8086
  targetPort: 8086

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8086"
  # ⚠️ docker-compose 가 creator_db 를 가리키고 있음. 실제 movie 데이터가 어디 저장되는지 조원 확인 후 수정 필요.
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/creator_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  SPRING_DATA_REDIS_HOST: "redis-master.dev.svc.cluster.local"
  SPRING_DATA_REDIS_PORT: "6379"
  DDL_AUTO: "validate"
  # ★ ES 폴백 모드 (메모리 한계로 ES 미배포)
  MOVIE_ES_ENABLED: "false"
  ES_URIS: "http://localhost:9200"   # 어차피 사용 안 함, env 누락 방지용

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: false       # movie 는 JWT 검증 gateway 가 처리한다고 가정
  toss: false
  aws: false
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 640Mi

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.4: values-payment.yaml (Toss)

**Files:**
- Create: `k8s/values/values-payment.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-payment.yaml`:

```yaml
# payment-service (포트 8081) — Toss Payments + Outbox 패턴 (PR #161)
# - DB: payment_db

image:
  repository: y0000h/payment-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8081
  targetPort: 8081

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8081"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/payment_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  DDL_AUTO: "validate"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: false       # gateway 에서 검증
  toss: true       # TOSS_PAYMENT_CK, TOSS_PAYMENT_SECRET 자동 주입
  aws: false
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 640Mi

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.5: values-ticket.yaml

**Files:**
- Create: `k8s/values/values-ticket.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-ticket.yaml`:

```yaml
# ticket-service (포트 8084) — Redis 동시성 제어
# - DB: ticket_db
# - user-service HTTP 호출 의존

image:
  repository: y0000h/ticket-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8084
  targetPort: 8084

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8084"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/ticket_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  SPRING_DATA_REDIS_HOST: "redis-master.dev.svc.cluster.local"
  SPRING_DATA_REDIS_PORT: "6379"
  CLIENT_USER_BASE_URL: "http://user-service.dev.svc.cluster.local:8085"
  DDL_AUTO: "validate"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: false
  toss: false
  aws: false
  openai: false

resources:
  requests:
    cpu: 150m
    memory: 448Mi    # 동시성 처리 메모리 더 줌
  limits:
    cpu: 600m
    memory: 768Mi    # docker-compose 와 동일

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.6: values-settlement.yaml

**Files:**
- Create: `k8s/values/values-settlement.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-settlement.yaml`:

```yaml
# settlement-service (포트 8083) — 정산 + DLQ 처리
# - DB: settlement_db
# - creator-service HTTP 호출 의존

image:
  repository: y0000h/settlement-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8083
  targetPort: 8083

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8083"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/settlement_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  CLIENT_CREATOR_BASE_URL: "http://creator-service.dev.svc.cluster.local:8080"
  SETTLEMENT_FEE_RATE: "0.1"
  SETTLEMENT_COOKIE_TO_KRW_RATE: "100"
  SETTLEMENT_DLQ_FALLBACK_PATH: "/tmp/settlement-dlq"
  SETTLEMENT_DLQ_MAX_RETRY: "5"
  SPRING_TASK_SCHEDULING_TIMEZONE: "Asia/Seoul"
  DDL_AUTO: "validate"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: false
  toss: false
  aws: false
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 640Mi

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.7: values-streaming.yaml (mem 1Gi, HLS)

> **💡 streaming-service 는 HLS 변환 부하 때문에 메모리 1Gi.** 영상 처리 중 OOM 발생 시 §1.5 폴백 시나리오 (mem 1.5Gi 상향 또는 별도 EC2 분리) 발동.

**Files:**
- Create: `k8s/values/values-streaming.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-streaming.yaml`:

```yaml
# streaming-service (포트 8088) — HLS 변환 + WebSocket 채팅 + Quartz JDBC
# - DB: streaming_db
# - creator-service HTTP 호출 의존
# - S3: 영상/HLS 세그먼트 저장
# - 메모리 1Gi (HLS 변환 부하)

image:
  repository: y0000h/streaming-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8088
  targetPort: 8088

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m"   # 일반 서비스보다 +heap
  SERVER_PORT: "8088"
  DB_HOST: "postgres.dev.svc.cluster.local"
  DB_PORT: "5432"
  DB_NAME: "streaming_db"
  DB_USERNAME: "postgres"
  DB_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  KAFKA_CONSUMER_GROUP_ID: "streaming-service"
  REDIS_HOST: "redis-master.dev.svc.cluster.local"
  REDIS_PORT: "6379"
  STREAMING_ADDRESS_ADAPTER: "k8s"      # 또는 docker-compose 와 동일 값
  STREAMING_PUBLIC_BASE_URL: "http://REPLACE_EIP"   # Phase 6 에서 EIP 로 교체
  CLIENT_CREATOR_BASE_URL: "http://creator-service.dev.svc.cluster.local:8080"
  STORAGE_S3_PATH: "streaming/"
  DDL_AUTO: "validate"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: true     # STREAMING_JWT_SECRET 등
  toss: false
  aws: true     # S3 영상/HLS
  openai: false

resources:
  requests:
    cpu: 200m
    memory: 768Mi
  limits:
    cpu: 1000m
    memory: 1Gi   # ★ 일반 서비스의 2배

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 120
  livenessPeriod: 30
  readinessInitialDelay: 45
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.8: values-ai.yaml (mem 1Gi, OpenAI)

**Files:**
- Create: `k8s/values/values-ai.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-ai.yaml`:

```yaml
# ai-service (포트 8089) — OpenAI 임베딩/요약/리랭킹 + pgvector 추천
# - DB: ai_db (pgvector 익스텐션 활성화 필요)
# - Kafka 이벤트 소비 (movie/ticket/user 토픽)
# - 메모리 1Gi (OpenAI 호출 + 임베딩 처리 부하)

image:
  repository: y0000h/ai-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8089
  targetPort: 8089

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m"
  SERVER_PORT: "8089"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.dev.svc.cluster.local:5432/ai_db"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "_OVERRIDE_FROM_SECRET_"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  SPRING_DATA_REDIS_HOST: "redis-master.dev.svc.cluster.local"
  SPRING_DATA_REDIS_PORT: "6379"
  DDL_AUTO: "validate"

sharedEnv: {}

secretRefs:
  postgres: true
  jwt: false
  toss: false
  aws: false
  openai: true     # OPENAI_API_KEY 자동 주입

resources:
  requests:
    cpu: 200m
    memory: 768Mi
  limits:
    cpu: 1000m
    memory: 1Gi

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 120
  livenessPeriod: 30
  readinessInitialDelay: 45
  readinessPeriod: 10

ingress:
  enabled: false
```

### Task 4.9: values-gateway.yaml (Ingress 활성)

> **💡 gateway 만 외부 노출.** Ingress 활성, 다른 서비스 라우팅 변수 모두 명시.

**Files:**
- Create: `k8s/values/values-gateway.yaml`

- [ ] **Step 1: 작성**

Create file `k8s/values/values-gateway.yaml`:

```yaml
# gateway-service (포트 8000) — 외부 진입점, JWT 검증, Spring Cloud Gateway 라우팅
# - DB 불필요 (Stateless)
# - Ingress 활성 (Traefik via EIP)

image:
  repository: y0000h/gateway-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  port: 8000
  targetPort: 8000

env:
  SPRING_PROFILES_ACTIVE: prod
  JAVA_OPTS: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
  SERVER_PORT: "8000"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "52.78.88.98:9092"
  # 라우팅 대상 (K3s ClusterIP DNS)
  USER_SERVICE_HOST: "http://user-service.dev.svc.cluster.local:8085"
  CREATOR_SERVICE_HOST: "http://creator-service.dev.svc.cluster.local:8080"
  # docker-compose 호환을 위해 BASE_IP / *_PORT 도 유지 (gateway 코드가 사용 시)
  BASE_IP: "dev.svc.cluster.local"
  CREATOR_PORT: "8080"
  PAYMENT_PORT: "8081"
  SETTLEMENT_PORT: "8083"
  TICKET_PORT: "8084"
  USER_PORT: "8085"
  MOVIE_PORT: "8086"
  STREAMING_PORT: "8088"
  AI_PORT: "8089"

sharedEnv: {}

secretRefs:
  postgres: false   # gateway DB 안 씀
  jwt: true          # JWT_TOKEN_PUBLIC 검증
  toss: false
  aws: false
  openai: false

resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 384Mi    # docker-compose 와 동일

healthcheck:
  enabled: true
  path: /actuator/health
  livenessInitialDelay: 90
  livenessPeriod: 30
  readinessInitialDelay: 30
  readinessPeriod: 10

ingress:
  enabled: true
  className: traefik
  annotations:
    # 큰 영상 업로드 대비 (gateway 가 streaming 으로 프록시)
    traefik.ingress.kubernetes.io/buffering-max-request-bytes: "524288000"  # 500MB
  hosts:
    - host: ""           # 도메인 없음 → IP 직접 접근 (Traefik 의 catch-all)
      paths:
        - path: /
          pathType: Prefix
  tls: []
```

### Task 4.10: values 파일 9개 일괄 검증 + commit

> **💡 9개 파일이 모두 차트에서 잘 렌더링되는지 한 번에 확인.**

**Files:** (없음, 검증만)

- [ ] **Step 1: 9개 파일 모두 helm template 렌더링 시도**

Run (본인 PC):
```bash
for svc in gateway user creator movie payment ticket settlement streaming ai; do
  echo "===== $svc ====="
  helm template $svc-service k8s/charts/microservice \
    -f k8s/values/values-$svc.yaml \
    --namespace dev > /tmp/render-$svc.yaml 2>&1 \
    && echo "OK: $(wc -l < /tmp/render-$svc.yaml) lines" \
    || echo "FAILED"
done
```

Expected:
```
===== gateway =====
OK: ~75 lines
===== user =====
OK: ~95 lines
... (9개 모두 OK)
```

만약 어떤 서비스가 FAILED:
```bash
helm template <svc>-service k8s/charts/microservice -f k8s/values/values-<svc>.yaml --debug
```
로 정확한 에러 메시지 확인.

- [ ] **Step 2: 렌더링된 매니페스트 한 개 샘플 확인**

Run:
```bash
cat /tmp/render-gateway.yaml | head -80
```

Expected: ConfigMap → Service → Deployment → Ingress 순서대로 출력. 모든 변수 채워져 있음.

- [ ] **Step 3: commit**

```bash
git add k8s/values/
git commit -m "feat(k3s): 9개 서비스 values 파일 (gateway Ingress 활성, streaming/ai mem 1Gi)"
```

---

## Phase 5: movie-service 시범 배포 (1개 먼저)

> **💡 9개 한꺼번에 배포 시 디버깅 지옥.** 가장 단순한 movie-service 1개로 먼저 검증 → OK면 나머지 8개 일괄 진행. 실패 시 원인 파악이 쉬움.

### Task 5.1: Docker Hub 이미지 존재 확인 (또는 빌드/푸시)

> **💡 K3s 가 Pod 띄울 때 Docker Hub 에서 이미지 pull 함.** `y0000h/movie-service:latest` 가 이미 존재한다면 그대로 사용. 없거나 오래되면 push 필요.

- [ ] **Step 1: Docker Hub 에서 이미지 존재 확인**

브라우저: https://hub.docker.com/r/y0000h/movie-service/tags

또는 Run:
```bash
docker manifest inspect y0000h/movie-service:latest
```

Expected: JSON 출력 (이미지 메타데이터). 에러면 이미지 없음 → Step 2.

- [ ] **Step 2: 이미지 없거나 갱신 필요한 경우만 — 로컬 빌드/푸시**

Run (프로젝트 루트):
```bash
docker login    # Docker Hub 계정 입력
cd movie-service
./gradlew clean build -x test
docker build -t y0000h/movie-service:latest .
docker push y0000h/movie-service:latest
cd ..
```

또는 GitHub Actions CI (현재 cd.yml) 가 이미 자동 푸시 중이면 main/dev/main 푸시 한 번 하면 자동 수행됨.

### Task 5.2: movie-service helm install + 검증

**Files:** (없음, 명령만)

- [ ] **Step 1: helm install (실제 K3s 에 배포)**

Run (본인 PC):
```bash
helm upgrade --install movie-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-movie.yaml \
  --wait --timeout 5m
```

Expected:
```
Release "movie-service" does not exist. Installing it now.
NAME: movie-service
LAST DEPLOYED: ...
NAMESPACE: dev
STATUS: deployed
REVISION: 1
```

- [ ] **Step 2: Pod 기동 watch (1~2분 소요)**

Run:
```bash
kubectl get pod -n dev -l app.kubernetes.io/instance=movie-service -w
```

Expected (60~90초 후):
```
NAME                              READY   STATUS    RESTARTS   AGE
movie-service-xxxxxxxx-xxxxx      1/1     Running   0          90s
```

`Ctrl+C` 종료.

- [ ] **Step 3: Pod 로그 확인 (Spring Boot 정상 기동)**

Run:
```bash
kubectl logs -n dev -l app.kubernetes.io/instance=movie-service --tail=50
```

Expected (마지막 부근):
```
... Started MovieServiceApplication in 35.xxx seconds (process running for 36.xxx)
```

- [ ] **Step 4: 헬스체크 확인 (Pod 안에서)**

Run:
```bash
kubectl exec -n dev -it deploy/movie-service -- wget -qO- http://localhost:8086/actuator/health
```

Expected:
```
{"status":"UP",...}
```

- [ ] **Step 5: ClusterIP 통한 접근 확인 (다른 Pod 에서)**

Run:
```bash
kubectl run curl-test --rm -it --image=curlimages/curl -n dev --restart=Never -- \
  curl -s http://movie-service.dev.svc.cluster.local:8086/actuator/health
```

Expected:
```
{"status":"UP",...}
```

- [ ] **Step 6: 트러블슈팅 — Pod 가 CrashLoopBackOff 인 경우**

```bash
kubectl describe pod -n dev -l app.kubernetes.io/instance=movie-service
kubectl logs -n dev -l app.kubernetes.io/instance=movie-service --previous
```

자주 나오는 에러:
- `Connection refused: postgres.dev.svc.cluster.local:5432` → Postgres Pod 상태 확인
- `Database "movie_db" does not exist` → 우리 plan 은 movie 가 creator_db 사용. URL 확인
- `OOMKilled` → resources.limits.memory 상향 또는 Xmx 축소

- [ ] **Step 7: ★ 시범 배포 성공 시 commit (코드 변경 없으니 메모만)**

여기서 commit 할 코드는 없음. 위 명령들이 잘 작동하면 다음 Phase 로 진행.

---

## Phase 6: 나머지 8개 서비스 + gateway 외부 노출

> **💡 시범이 성공했으니 나머지 7개 + gateway 한 번에.** 의존성 순서: user → creator → payment/ticket/settlement → streaming → ai → gateway. 하나씩 띄우고 메모리 확인 후 다음으로.

### Task 6.1: gateway-service 라우팅 코드 사전 수정 (review 통합)

> **💡 이게 뭐냐면**
> docker-compose 시절 gateway 의 `application-prod.yaml` 에 `review-service` 라우팅이 있었다면 K3s 에서는 `user-service` 로 통합되었으므로 라우팅 경로 정리 필요.

**Files:**
- Modify: `gateway-service/src/main/resources/application-prod.yaml`

- [ ] **Step 1: 현재 라우팅 설정 확인**

Run:
```bash
grep -n "review" gateway-service/src/main/resources/application-prod.yaml || echo "no review references"
```

Expected: 없으면 "no review references" 출력 → Step 4 로 skip.

- [ ] **Step 2: review-service 라우팅 entry 가 있다면 user-service 로 합치기**

Edit `gateway-service/src/main/resources/application-prod.yaml`:

`spring.cloud.gateway.routes` 안에서 `review-service` 라우팅 블록을 삭제하고, `user-service` 라우팅의 path 패턴에 `/api/reviews/**` 추가:

```yaml
# Before:
- id: review-service
  uri: http://review-service:8087
  predicates:
    - Path=/api/reviews/**

# After: (block 삭제, user-service 의 predicates 에 추가)
- id: user-service
  uri: ${USER_SERVICE_HOST}
  predicates:
    - Path=/api/users/**, /api/auth/**, /api/reviews/**
```

(실제 yaml 들여쓰기는 본인 파일 그대로 유지)

- [ ] **Step 3: 라우팅 변경 commit**

```bash
git add gateway-service/src/main/resources/application-prod.yaml
git commit -m "refactor(gateway): review-service 라우팅을 user-service 로 통합"
```

- [ ] **Step 4: 변경된 gateway 이미지 재빌드 + 푸시**

Run (프로젝트 루트):
```bash
cd gateway-service
./gradlew clean build -x test
docker build -t y0000h/gateway-service:latest .
docker push y0000h/gateway-service:latest
cd ..
```

(또는 commit 후 GitHub Actions CI 가 자동 푸시할 때까지 대기 — 약 5~10분)

### Task 6.2: 의존성 낮은 서비스부터 일괄 배포 (user → creator → payment → ticket → settlement)

> **💡 이게 뭐냐면**
> 5개 서비스를 차례로 helm install. 매 배포 후 `kubectl get pod -n dev` 로 메모리 누적 확인. 이상 신호 (Pending, OOMKilled, ImagePullBackOff) 발생 시 즉시 중단하고 원인 파악.

- [ ] **Step 1: user-service 배포**

```bash
helm upgrade --install user-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-user.yaml \
  --wait --timeout 5m
```

검증:
```bash
kubectl get pod -n dev -l app.kubernetes.io/instance=user-service
kubectl top pod -n dev    # 누적 메모리 확인
```

- [ ] **Step 2: creator-service 배포**

```bash
helm upgrade --install creator-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-creator.yaml \
  --wait --timeout 5m
```

검증: `kubectl get pod -n dev`, `kubectl top pod -n dev`.

- [ ] **Step 3: payment-service 배포**

```bash
helm upgrade --install payment-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-payment.yaml \
  --wait --timeout 5m
```

- [ ] **Step 4: ticket-service 배포**

```bash
helm upgrade --install ticket-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-ticket.yaml \
  --wait --timeout 5m
```

- [ ] **Step 5: settlement-service 배포**

```bash
helm upgrade --install settlement-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-settlement.yaml \
  --wait --timeout 5m
```

- [ ] **Step 6: 5개 서비스 + 기존 movie 메모리 누적 확인**

```bash
kubectl top pod -n dev
free -h    # EC2 안에서 (ssh 접속 후)
```

기대치 (보수적):
- 6개 Spring Boot Pod × 평균 350MB = ~2.1GB
- + Postgres ~200MB + Redis ~30MB + K3s ~500MB
- = 약 2.8GB
- 노드 가용 ~5GB 남음 → streaming/ai 들어갈 자리 있음

만약 어떤 Pod 메모리 limit 근접 (`kubectl top pod` 에서 limits 의 90% 이상): JVM `-Xmx` 더 줄이거나 limit 상향 검토.

### Task 6.3: streaming-service / ai-service 배포 (메모리 1Gi)

> **💡 가장 부담 큰 두 서비스.** 한 번에 둘 다 띄우지 말고 streaming 먼저 띄워 메모리 확인 후 ai.

- [ ] **Step 1: streaming-service 배포**

```bash
helm upgrade --install streaming-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-streaming.yaml \
  --wait --timeout 5m
```

검증:
```bash
kubectl get pod -n dev -l app.kubernetes.io/instance=streaming-service
kubectl logs -n dev -l app.kubernetes.io/instance=streaming-service --tail=30
kubectl top pod -n dev | grep streaming
```

streaming-service 메모리가 800MB 이상이면 limit 1Gi 근접 → 추후 부하 시 OOM 위험.

- [ ] **Step 2: ai-service 배포**

```bash
helm upgrade --install ai-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-ai.yaml \
  --wait --timeout 5m
```

검증:
```bash
kubectl get pod -n dev -l app.kubernetes.io/instance=ai-service
kubectl logs -n dev -l app.kubernetes.io/instance=ai-service --tail=30
```

만약 OpenAI 키 검증 단계에서 실패: `kubectl describe pod` 로 환경변수 주입 확인.

- [ ] **Step 3: 8개 서비스 동시 가동 메모리 체크 (gateway 제외)**

```bash
kubectl top pod -n dev
free -h    # SSH for EC2
```

기대치 (보수적):
- 일반 6개 (movie, user, creator, payment, ticket, settlement) × 350MB = ~2.1GB
- streaming + ai × 800MB = ~1.6GB
- + 인프라 (Postgres, Redis, K3s) = ~700MB
- = 약 4.4GB / 8GB 사용. 남은 ~3.5GB 가 swap 으로 빠지면 OK.

만약 free -h 에서 swap 사용 중이면 정상 (swap 으로 보호되는 중).

### Task 6.4: gateway-service 배포 + Ingress 외부 노출

> **💡 마지막 piece.** gateway 가 떠야 외부 트래픽이 들어옴. helm install 후 EIP 로 외부 접속 검증.

- [ ] **Step 1: gateway-service 배포**

```bash
helm upgrade --install gateway-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-gateway.yaml \
  --wait --timeout 5m
```

- [ ] **Step 2: Ingress 리소스 생성 확인**

```bash
kubectl get ingress -n dev
```

Expected:
```
NAME              CLASS     HOSTS   ADDRESS         PORTS   AGE
gateway-service   traefik   *       10.0.x.x        80      30s
```

ADDRESS 가 비어있다면 Traefik 가 아직 못 잡은 것 — 1~2분 대기.

- [ ] **Step 3: 본인 PC 에서 EIP 로 직접 호출**

Run (본인 PC, EIP 본인 것으로 교체):
```bash
curl -v http://13.125.xxx.xxx/actuator/health
```

Expected:
```
< HTTP/1.1 200 OK
{"status":"UP",...}
```

만약 `Connection refused`:
- AWS 보안그룹의 80번 포트가 0.0.0.0/0 에서 들어올 수 있는지 확인 (Task 1.1 Step 4)
- EC2 안에서 `sudo ss -tlnp | grep ':80 '` 로 Traefik 가 80 듣고 있는지 확인

만약 `404 Not Found` (Traefik 응답): Ingress 의 host/path 매칭 실패 → `kubectl describe ingress -n dev` 확인.

- [ ] **Step 4: 핵심 비즈니스 API 1개 smoke test**

영화 목록 조회 (인증 불필요한 endpoint 가정):
```bash
curl -v http://13.125.xxx.xxx/api/movies?page=0&size=5
```

Expected: 200 또는 401 (JWT 누락 - 정상). 502/503 이면 gateway → 내부 서비스 라우팅 실패.

- [ ] **Step 5: 9개 서비스 모두 떠있는지 최종 확인**

```bash
kubectl get pod -n dev
kubectl get svc -n dev
kubectl get ingress -n dev
helm list -n dev
```

Expected: 9개 helm release 모두 deployed, 9개 Pod 모두 Running, gateway 의 Ingress ADDRESS 채워짐.

- [ ] **Step 6: 메모리/디스크 최종 점검**

```bash
# 본인 PC 에서
kubectl top node

# EC2 SSH 접속해서
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx
free -h
df -h /var/lib/rancher/k3s
exit
```

기대치:
- node CPU < 50%, memory < 90%
- swap 사용 < 4GB (8GB 한도 중)
- 디스크 < 30GB (50GB 중)

---

## Phase 7: CI/CD 자동화 (`cd.yml` 교체)

> **💡 Phase 6 까지는 본인 PC 에서 helm 명령으로 수동 배포.** Phase 7 부터는 GitHub 에 push 하면 GitHub Actions 가 자동으로 SSH → helm upgrade 수행. 진짜 자동화 완성.

### Task 7.1: GitHub Secrets 등록

> **💡 GitHub Actions 가 EC2 SSH 접속할 때 필요한 정보를 secret 으로 미리 등록.** Repo Settings → Secrets and variables → Actions.

**Files:** (없음 — GitHub UI)

- [ ] **Step 1: 기존 secrets 확인**

브라우저: `https://github.com/prgrms-be-adv-devcourse/beadv5_5_3M_BE/settings/secrets/actions`

기존 등록된 것 확인:
- `DOCKERHUB_USERNAME` ✓ (유지)
- `DOCKERHUB_TOKEN` ✓ (유지)
- `EC2_SSH_KEY` (있을 수도, 새 키와 다를 수 있음)

- [ ] **Step 2: `EC2_ELASTIC_IP` 신규 등록**

1. 우측 `New repository secret` 클릭
2. Name: `EC2_ELASTIC_IP`
3. Value: 본인 EIP (예: `13.125.xxx.xxx`)
4. `Add secret`

- [ ] **Step 3: `EC2_SSH_KEY` 갱신 (또는 신규 등록)**

본인 PC 의 `~/.ssh/beadv5-k3s-key.pem` 파일 전체 내용을 그대로:

1. `New repository secret` (또는 기존 `EC2_SSH_KEY` 의 `Update`)
2. Name: `EC2_SSH_KEY`
3. Value: PEM 파일 전체 내용 (`-----BEGIN RSA PRIVATE KEY-----` 부터 `-----END RSA PRIVATE KEY-----` 까지 줄바꿈 포함 모두)
4. `Add secret`

- [ ] **Step 4: `EC2_USER` 추가 (Ubuntu AMI 라 ubuntu)**

1. `New repository secret`
2. Name: `EC2_USER`
3. Value: `ubuntu`
4. `Add secret`

- [ ] **Step 5: `EC2_HOST` 가 있다면 정리 (선택)**

기존 `EC2_HOST` 가 옛 인스턴스 도메인/IP 라면 더 이상 사용 안 함. 삭제하거나 그대로 두기.

### Task 7.2: EC2 안에 git clone (deploy 시 코드 동기화 위해 — 1회)

> **💡 cd.yml 의 deploy job 이 EC2 안에서 `git pull` 한 뒤 helm upgrade 하므로, EC2 에 미리 repo 가 clone 되어 있어야 함.**

- [ ] **Step 1: EC2 SSH + 도구 설치**

Run:
```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx
```

EC2 안에서:
```bash
sudo apt install -y git
```

- [ ] **Step 2: helm + kubectl 도 EC2 안에 설치 (deploy job 이 사용)**

EC2 안에서:
```bash
# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# helm
curl https://baltocdn.com/helm/signing.asc | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt update && sudo apt install -y helm

# 검증
kubectl version --client
helm version
```

- [ ] **Step 3: kubectl 의 KUBECONFIG 권한 (ubuntu 사용자가 접근 가능하게)**

EC2 안에서:
```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown ubuntu:ubuntu ~/.kube/config
chmod 600 ~/.kube/config
echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc
source ~/.bashrc

# 검증
kubectl get nodes
```

- [ ] **Step 4: repo clone**

EC2 안에서:
```bash
cd ~
git clone https://github.com/prgrms-be-adv-devcourse/beadv5_5_3M_BE.git
cd beadv5_5_3M_BE
git checkout dev/main    # CI/CD 가 머지하는 브랜치
```

만약 private repo 라 인증 필요하면 GitHub Personal Access Token 사용:
```bash
git clone https://<USERNAME>:<TOKEN>@github.com/prgrms-be-adv-devcourse/beadv5_5_3M_BE.git
```

- [ ] **Step 5: 종료**

```bash
exit
```

### Task 7.3: `cd.yml` 의 deploy job 교체

> **💡 핵심 변경: `deploy-to-ec2` job 의 SCP+SSH+`docker compose` 를 SSH+`helm upgrade` 로 교체.**

**Files:**
- Modify: `.github/workflows/cd.yml`

- [ ] **Step 1: 기존 `cd.yml` 읽기**

Run:
```bash
cat .github/workflows/cd.yml | head -100
```

기존 `deploy-to-ec2` job 의 위치 + 형식 파악.

- [ ] **Step 2: `deploy-to-ec2` job 전체 교체**

Edit `.github/workflows/cd.yml`:

기존 `deploy-to-ec2:` 시작부터 그 job 의 끝(다음 job 또는 파일 끝)까지를 다음으로 교체:

```yaml
  deploy-to-k3s:
    needs: [detect-changes, docker-build-push]
    if: needs.detect-changes.outputs.has_changes == 'true'
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      max-parallel: 1   # K3s 1노드라 동시 배포 시 Pod 새로 띄울 메모리 부족 → 직렬 처리
      matrix:
        service: ${{ fromJson(needs.detect-changes.outputs.services) }}
    steps:
      - name: Skip non-deployable directories
        id: filter
        run: |
          # K3s 에 배포되지 않는 디렉토리 필터링 (reivew-service 오타 디렉토리 등)
          if [[ "${{ matrix.service }}" == "reivew-service" ]]; then
            echo "skip=true" >> "$GITHUB_OUTPUT"
            echo "::notice::reivew-service 는 user 통합으로 K3s 배포 대상 아님 — skip"
          else
            echo "skip=false" >> "$GITHUB_OUTPUT"
          fi

      - name: Deploy via SSH + helm upgrade
        if: steps.filter.outputs.skip != 'true'
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_ELASTIC_IP }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          timeout: 5m
          command_timeout: 10m
          script: |
            set -euo pipefail

            cd ~/beadv5_5_3M_BE

            # 최신 코드 가져오기 (k8s/ 디렉토리 동기화)
            git fetch origin
            git checkout ${{ github.ref_name }}
            git pull origin ${{ github.ref_name }}

            SVC="${{ matrix.service }}"        # e.g., movie-service
            SVC_SHORT="${SVC%-service}"        # e.g., movie

            VALUES_FILE="./k8s/values/values-${SVC_SHORT}.yaml"
            if [ ! -f "$VALUES_FILE" ]; then
              echo "::warning::$VALUES_FILE 가 없습니다. K3s 배포 skip ($SVC)"
              exit 0
            fi

            export KUBECONFIG=~/.kube/config

            helm upgrade --install "$SVC" ./k8s/charts/microservice \
              --namespace dev \
              --create-namespace \
              -f "$VALUES_FILE" \
              --set image.repository=${{ secrets.DOCKERHUB_USERNAME }}/$SVC \
              --set image.tag=${{ github.sha }} \
              --wait --timeout 5m

            # 배포 후 Pod 상태 출력
            kubectl rollout status deploy/$SVC -n dev --timeout=2m
            kubectl get pod -n dev -l app.kubernetes.io/instance=$SVC
```

> **⚠️ 주의:**
> - 기존 cd.yml 의 `deploy-to-ec2` job 만 교체. `detect-changes`, `docker-build-push` 등 다른 job 은 그대로 유지.
> - `${{ github.sha }}` 를 image.tag 로 사용 → 모든 commit 마다 unique 한 이미지 태그. 이미지가 Docker Hub 에 그 sha 로 푸시되어 있어야 함 (기존 `docker-build-push` job 이 그 역할 수행 가정).

- [ ] **Step 3: yaml 문법 검증**

Run (본인 PC):
```bash
# yamllint 가 있으면
yamllint .github/workflows/cd.yml || true

# 또는 git에 commit 전 시각 확인만
cat .github/workflows/cd.yml | grep -A 5 "deploy-to-k3s:"
```

- [ ] **Step 4: 변경 commit**

```bash
git add .github/workflows/cd.yml
git commit -m "ci(cd): deploy-to-ec2 → deploy-to-k3s (helm upgrade via SSH)"
```

### Task 7.4: 자동 배포 동작 검증

> **💡 PR 만들고 dev/main 으로 머지 → GitHub Actions 가 deploy-to-k3s 자동 수행 확인.**

- [ ] **Step 1: 의도적 작은 변경 (movie-service README)**

```bash
echo "" >> movie-service/README.md
echo "# K3s 배포 검증 $(date +%Y%m%d-%H%M)" >> movie-service/README.md
git add movie-service/README.md
git commit -m "test(ci): K3s deploy job 동작 검증 (movie README touch)"
```

- [ ] **Step 2: 작업 브랜치 push + PR 생성**

```bash
git push -u origin feature/k3s-deployment
```

GitHub UI 에서:
1. https://github.com/prgrms-be-adv-devcourse/beadv5_5_3M_BE/pulls
2. `Compare & pull request` 클릭 (방금 푸시한 브랜치)
3. base: `dev/main` ← compare: `feature/k3s-deployment`
4. PR 제목: `feat(k3s): K3s on EC2 단일 노드 배포 + CI/CD 자동화`
5. PR 본문: spec/plan 링크 첨부
6. `Create pull request`
7. (CI 통과 + 리뷰 후) Merge

- [ ] **Step 3: 머지 직후 GitHub Actions 탭 확인**

브라우저: `https://github.com/.../actions`

- `cd.yml` workflow 가 trigger 됨
- `detect-changes` job → `services: ["movie-service"]` 출력 확인
- `docker-build-push` job → 이미지 푸시 성공
- `deploy-to-k3s` job (`movie-service` matrix) → SSH 접속 → helm upgrade 성공

- [ ] **Step 4: 배포 결과 확인 (본인 PC)**

```bash
helm list -n dev | grep movie-service
kubectl get pod -n dev -l app.kubernetes.io/instance=movie-service
```

이미지 tag 가 commit sha 로 바뀌어 있어야 함:
```bash
kubectl get pod -n dev -l app.kubernetes.io/instance=movie-service -o jsonpath='{.items[0].spec.containers[0].image}'
```

Expected: `y0000h/movie-service:abc123def456...` (sha)

---

## Phase 8: 운영 자동화 (백업 + 로그 관리)

> **💡 9개 서비스 다 잘 떠 있어도 며칠 지나면 디스크 폭주 / DB 데이터 위험.** 자동화로 보호.

### Task 8.1: pg_dump → S3 일일 백업 CronJob

> **💡 in-cluster Postgres 는 노드 죽으면 데이터도 같이 죽음.** 매일 새벽 2시 자동 dump → S3 저장. 노드 장애 복구 시 가장 최근 dump 로 복원.

**Files:**
- Create: `k8s/ops/pg-backup-cronjob.yaml`

- [ ] **Step 1: CronJob 매니페스트 작성**

Create file `k8s/ops/pg-backup-cronjob.yaml`:

```yaml
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: pg-backup
  namespace: dev
  labels:
    app: pg-backup
spec:
  schedule: "0 17 * * *"   # UTC 17:00 = KST 02:00
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: pg-backup
              image: postgres:16-alpine
              imagePullPolicy: IfNotPresent
              command: ["/bin/sh", "-c"]
              args:
                - |
                  set -euo pipefail
                  apk add --no-cache aws-cli
                  TS=$(date +%Y%m%d_%H%M%S)
                  DUMP_FILE="/tmp/pg_all_${TS}.sql.gz"

                  echo "[1/2] pg_dumpall 실행..."
                  PGPASSWORD="$POSTGRES_PASSWORD" pg_dumpall \
                    -h postgres.dev.svc.cluster.local \
                    -U "$POSTGRES_USER" \
                    --clean --if-exists \
                    | gzip > "$DUMP_FILE"

                  SIZE=$(du -h "$DUMP_FILE" | cut -f1)
                  echo "[1/2] 완료. 파일 크기: $SIZE"

                  echo "[2/2] S3 업로드: s3://${AWS_S3_BUCKET}/backups/pg/${TS}.sql.gz"
                  aws s3 cp "$DUMP_FILE" "s3://${AWS_S3_BUCKET}/backups/pg/${TS}.sql.gz" \
                    --region "$AWS_REGION"
                  echo "[2/2] 업로드 완료."

                  echo "[3/3] 7일 이상된 dump 삭제..."
                  aws s3 ls "s3://${AWS_S3_BUCKET}/backups/pg/" --region "$AWS_REGION" \
                    | awk '$1 < "'"$(date -d '7 days ago' +%Y-%m-%d)"'"{print $4}' \
                    | xargs -I {} aws s3 rm "s3://${AWS_S3_BUCKET}/backups/pg/{}" --region "$AWS_REGION" || true
                  echo "[3/3] cleanup 완료."
              envFrom:
                - secretRef:
                    name: postgres-secret
                - secretRef:
                    name: aws-secret
              resources:
                requests:
                  cpu: 100m
                  memory: 128Mi
                limits:
                  cpu: 500m
                  memory: 512Mi
```

- [ ] **Step 2: 적용**

```bash
kubectl apply -f k8s/ops/pg-backup-cronjob.yaml
```

Expected:
```
cronjob.batch/pg-backup created
```

- [ ] **Step 3: 즉시 1회 실행해서 동작 검증 (스케줄 기다리지 말고)**

```bash
kubectl create job pg-backup-manual-test --from=cronjob/pg-backup -n dev
kubectl get pod -n dev -l job-name=pg-backup-manual-test -w
```

Expected: `Completed` 상태로 종료.

로그:
```bash
kubectl logs -n dev -l job-name=pg-backup-manual-test
```

Expected:
```
[1/2] pg_dumpall 실행...
[1/2] 완료. 파일 크기: ~xxxKB
[2/2] S3 업로드: s3://beadv5-uploads-1/backups/pg/...
[2/2] 업로드 완료.
[3/3] 7일 이상된 dump 삭제...
[3/3] cleanup 완료.
```

- [ ] **Step 4: S3 콘솔에서 파일 확인**

브라우저: `https://s3.console.aws.amazon.com/s3/buckets/beadv5-uploads-1?region=ap-northeast-2`

`backups/pg/` 폴더 안에 `YYYYMMDD_HHMMSS.sql.gz` 파일 보임.

- [ ] **Step 5: 수동 job 정리**

```bash
kubectl delete job pg-backup-manual-test -n dev
```

- [ ] **Step 6: commit**

```bash
git add k8s/ops/pg-backup-cronjob.yaml
git commit -m "feat(k3s): pg_dump → S3 일일 백업 CronJob (KST 02:00, 7일 retention)"
```

### Task 8.2: 호스트 EC2 logrotate + 도커 prune cron

> **💡 컨테이너 로그가 누적되면 50GB EBS 가 빠르게 참.** 호스트 logrotate 로 일일 회전 + 주간 도커 이미지 prune.

**Files:**
- Create: `k8s/ops/logrotate.conf`

- [ ] **Step 1: logrotate 설정 파일 작성**

Create file `k8s/ops/logrotate.conf`:

```
# /etc/logrotate.d/docker-containers (호스트 EC2 에 배포)
/var/log/pods/*/*/*.log /var/lib/docker/containers/*/*.log {
    rotate 5
    daily
    compress
    delaycompress
    missingok
    copytruncate
    size 50M
    notifempty
}
```

- [ ] **Step 2: EC2 에 적용**

본인 PC 에서:
```bash
scp -i ~/.ssh/beadv5-k3s-key.pem k8s/ops/logrotate.conf ubuntu@13.125.xxx.xxx:/tmp/logrotate.conf
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@13.125.xxx.xxx
```

EC2 안에서:
```bash
sudo cp /tmp/logrotate.conf /etc/logrotate.d/docker-containers
sudo chmod 644 /etc/logrotate.d/docker-containers
sudo logrotate -d /etc/logrotate.d/docker-containers   # dry-run 검증
```

Expected: 에러 없이 "would rotate" 메시지들 출력.

- [ ] **Step 3: 주간 docker 이미지/볼륨 정리 cron**

EC2 안에서:
```bash
sudo crontab -e
```

마지막에 추가 (매주 일요일 03:00):
```
0 3 * * 0 docker system prune -af --volumes >> /var/log/docker-prune.log 2>&1
```

저장 후:
```bash
sudo crontab -l    # 등록 확인
exit
```

- [ ] **Step 4: commit**

```bash
git add k8s/ops/logrotate.conf
git commit -m "feat(k3s): EC2 호스트 logrotate + 주간 docker prune 가이드"
```

### Task 8.3: 운영 README 작성

> **💡 며칠/몇 주 뒤 본인이 다시 봤을 때, 또는 조원이 봤을 때 운영 명령을 한눈에 찾을 수 있게.**

**Files:**
- Create: `k8s/README.md`

- [ ] **Step 1: README 작성**

Create file `k8s/README.md`:

```markdown
# beadv5 K3s 운영 가이드

K3s on EC2 t3.large (단일 노드) 환경의 운영 명령어 모음.

## 클러스터 접속

```bash
# 본인 PC 에서
export KUBECONFIG=~/.kube/config-beadv5-k3s
kubectl get nodes

# EC2 SSH 접속
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@<EIP>
```

## 자주 쓰는 명령

### 전체 상태 보기
```bash
kubectl get all -n dev
helm list -n dev
kubectl top pod -n dev
```

### 특정 서비스 로그
```bash
kubectl logs -n dev -l app.kubernetes.io/instance=movie-service --tail=100 -f
```

### Pod 안 진입
```bash
kubectl exec -n dev -it deploy/movie-service -- sh
```

### 특정 서비스 수동 재배포
```bash
helm upgrade --install movie-service ./charts/microservice \
  --namespace dev \
  -f ./values/values-movie.yaml \
  --set image.tag=<commit-sha> \
  --wait
```

### 특정 서비스 일시 중단 (메모리 폴백)
```bash
kubectl scale deploy/ai-service --replicas=0 -n dev
# 복구
kubectl scale deploy/ai-service --replicas=1 -n dev
```

### Postgres 백업 즉시 실행
```bash
kubectl create job pg-backup-manual --from=cronjob/pg-backup -n dev
kubectl logs -n dev -l job-name=pg-backup-manual -f
```

### Postgres 백업 복원 (재해 복구)
```bash
# 1. 최근 dump 다운로드
aws s3 ls s3://beadv5-uploads-1/backups/pg/
aws s3 cp s3://beadv5-uploads-1/backups/pg/<latest>.sql.gz /tmp/
gunzip /tmp/<latest>.sql.gz

# 2. Postgres 에 복원
kubectl cp /tmp/<latest>.sql dev/postgres-xxx:/tmp/
kubectl exec -n dev -it deploy/postgres -- psql -U postgres -f /tmp/<latest>.sql
```

## 트러블슈팅

| 증상 | 원인 후보 | 해결 |
|---|---|---|
| Pod CrashLoopBackOff | OOM, DB 연결 실패, 환경변수 누락 | `kubectl describe pod`, `kubectl logs --previous` |
| Pod Pending | 노드 자원 부족 | `kubectl describe node` 의 Events 확인. 가장 덜 중요한 서비스 replicas: 0 |
| ImagePullBackOff | Docker Hub 이미지 없음 / private repo 인증 | `docker pull <image>` 로컬 검증 |
| 502 Bad Gateway (외부) | gateway 의 upstream Service 다운 | `kubectl get svc -n dev`, `kubectl get endpoints -n dev` |
| EBS 디스크 가득 | 컨테이너 이미지 누적 | EC2 에서 `docker system prune -af --volumes` |
| 메모리 90% 초과 | 너무 많은 서비스 동시 가동 | `kubectl top pod -n dev` 확인 후 가장 큰 것 replicas: 0 |

## 비용 정리 (시연 종료)

```bash
# 1. K3s 클러스터 정리 (볼륨 + 영상 데이터 보존 옵션)
helm uninstall $(helm list -n dev -q) -n dev
kubectl delete namespace dev

# 2. EC2 정지 (시간당 과금 멈춤)
# AWS 콘솔 → EC2 → Instances → beadv5-k3s-node → Instance state → Stop instance

# 3. 완전 종료 (EBS, EIP 까지 다 정리)
# AWS 콘솔 → Terminate instance → Release Elastic IP → Delete EBS volume
```

## 참고

- 설계서: [docs/superpowers/specs/k3s-ec2-deployment-design.md](../docs/superpowers/specs/k3s-ec2-deployment-design.md)
- 구현 계획서: [docs/superpowers/plans/k3s-ec2-deployment.md](../docs/superpowers/plans/k3s-ec2-deployment.md)
- 팀 1페이지 요약: [docs/k8s-team-summary.md](../docs/k8s-team-summary.md)
```

- [ ] **Step 2: commit**

```bash
git add k8s/README.md
git commit -m "docs(k3s): 운영 가이드 (kubectl 명령, 트러블슈팅, 비용 정리)"
```

### Task 8.4: 팀 공유용 1페이지 요약 갱신

> **💡 기존 `docs/k8s-team-summary.md` 는 EKS 가정으로 작성됨. K3s 변경 반영.**

**Files:**
- Modify: `docs/k8s-team-summary.md`

- [ ] **Step 1: 기존 파일 읽기**

```bash
cat docs/k8s-team-summary.md | head -30
```

- [ ] **Step 2: EKS → K3s 핵심 부분 갱신**

Edit `docs/k8s-team-summary.md`:

상단 TL;DR 부분과 비용 표, 아키텍처 다이어그램을 K3s 기준으로 교체. 자세한 변경은 spec [§3 Q1-Q12 표](../specs/k3s-ec2-deployment-design.md) 참조.

핵심 변경점만 요약:
- AWS EKS → K3s on EC2 t3.large 1대
- t3.large × 2 + Autoscaler → t3.large × 1
- ALB → Traefik (K3s 내장) + Elastic IP
- AWS Secrets Manager + ESO → K8s Secret 직접
- RDS db.t3.medium → in-cluster Postgres + S3 백업
- 비용 ~$280/월 → $0 (받은 자원만)

(전체 파일 새로 작성하는 건 plan 범위를 넘으므로, 본 task 는 핵심만 갱신)

- [ ] **Step 3: commit**

```bash
git add docs/k8s-team-summary.md
git commit -m "docs(k3s): 팀 공유용 1페이지 요약 K3s 변경 반영"
```

---

## (선택) Phase 9: 도메인 + Let's Encrypt SSL

> **💡 도메인 결정 후에만 진행.** 없으면 본 Phase skip.

### Task 9.1: cert-manager 설치

**Files:** (없음, helm 명령만)

- [ ] **Step 1: cert-manager Helm 설치**

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update

helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --version v1.15.3 \
  --set installCRDs=true \
  --set resources.requests.cpu=50m \
  --set resources.requests.memory=64Mi \
  --set resources.limits.memory=128Mi \
  --wait --timeout 5m
```

검증:
```bash
kubectl get pod -n cert-manager
```

Expected: 3개 Pod (cert-manager, cert-manager-cainjector, cert-manager-webhook) Running.

### Task 9.2: ClusterIssuer (Let's Encrypt) 생성

**Files:**
- Create: `k8s/infra/05-cluster-issuer.yaml`

- [ ] **Step 1: 매니페스트 작성 (이메일을 본인 것으로)**

Create file `k8s/infra/05-cluster-issuer.yaml`:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    email: y0000h2@gmail.com
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
      - http01:
          ingress:
            class: traefik
```

- [ ] **Step 2: 적용**

```bash
kubectl apply -f k8s/infra/05-cluster-issuer.yaml
```

### Task 9.3: 도메인 → EIP A 레코드 + values-gateway.yaml 갱신

**Files:**
- Modify: `k8s/values/values-gateway.yaml`

- [ ] **Step 1: 도메인 DNS 설정 (외부 작업)**

도메인 등록업체(또는 Route 53)에서 A 레코드:
- Name: `@` (또는 `api`, `app` 등 서브도메인)
- Type: `A`
- Value: `13.125.xxx.xxx` (EIP)
- TTL: 300

- [ ] **Step 2: values-gateway.yaml 의 ingress 섹션 갱신 (도메인 + tls)**

Edit `k8s/values/values-gateway.yaml`, ingress 섹션 교체:

```yaml
ingress:
  enabled: true
  className: traefik
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    traefik.ingress.kubernetes.io/buffering-max-request-bytes: "524288000"
  hosts:
    - host: api.YOUR-DOMAIN.com    # 본인 도메인으로
      paths:
        - path: /
          pathType: Prefix
  tls:
    - hosts:
        - api.YOUR-DOMAIN.com
      secretName: gateway-tls
```

- [ ] **Step 3: gateway 재배포**

```bash
helm upgrade gateway-service k8s/charts/microservice \
  --namespace dev \
  -f k8s/values/values-gateway.yaml \
  --reuse-values \
  --wait
```

- [ ] **Step 4: 인증서 발급 watch (1~3분 소요)**

```bash
kubectl get certificate -n dev -w
```

Expected:
```
NAME           READY   SECRET         AGE
gateway-tls    True    gateway-tls    2m
```

- [ ] **Step 5: HTTPS 검증**

```bash
curl -v https://api.YOUR-DOMAIN.com/actuator/health
```

Expected: `HTTP/1.1 200 OK` + 유효한 SSL 인증서.

- [ ] **Step 6: commit**

```bash
git add k8s/infra/05-cluster-issuer.yaml k8s/values/values-gateway.yaml
git commit -m "feat(k3s): 도메인 + Let's Encrypt SSL (cert-manager)"
```

---

## (선택) Phase 10: 별도 EC2 분리 폴백 (OOM 발생 시)

> **💡 9개 동시 가동에서 OOM 빈발 시.** 자원 추가 협상 → 별도 EC2 1대 더 받으면 ai/streaming 만 새 노드로 분리.

### Task 10.1: 두 번째 EC2 + K3s agent join

> **💡 K3s 에서 worker 노드 추가는 한 줄 명령으로 가능.**

- [ ] **Step 1: 마스터 노드의 join token 획득**

EC2 마스터 SSH 접속 후:
```bash
sudo cat /var/lib/rancher/k3s/server/node-token
```

토큰 메모.

- [ ] **Step 2: 새 EC2 생성 (Phase 1 절차와 동일)**

같은 보안그룹/같은 VPC. 단, EIP 는 안 붙여도 됨 (workder 는 외부 노출 X).

- [ ] **Step 3: 새 EC2 SSH 접속 후 K3s agent 설치**

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.30.3+k3s1 \
  K3S_URL=https://<마스터-EIP>:6443 \
  K3S_TOKEN=<위에서 메모한 token> \
  sh -
```

- [ ] **Step 4: 마스터에서 노드 추가 확인**

본인 PC 에서:
```bash
kubectl get nodes
```

Expected: 2개 노드 모두 Ready.

### Task 10.2: ai/streaming 만 새 노드로 격리

> **💡 nodeSelector 로 특정 Pod 가 어떤 노드에 떠야 하는지 강제.**

- [ ] **Step 1: 새 노드에 label 부여**

```bash
kubectl label nodes <새노드-이름> workload=heavy
```

- [ ] **Step 2: values-streaming.yaml + values-ai.yaml 에 nodeSelector 추가**

각 파일 마지막에 추가:
```yaml
nodeSelector:
  workload: heavy
```

- [ ] **Step 3: 차트 templates/deployment.yaml 에 nodeSelector 지원 추가**

Edit `k8s/charts/microservice/templates/deployment.yaml`, `spec.template.spec` 안에:
```yaml
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

- [ ] **Step 4: 두 서비스 재배포**

```bash
helm upgrade streaming-service k8s/charts/microservice -n dev -f k8s/values/values-streaming.yaml
helm upgrade ai-service k8s/charts/microservice -n dev -f k8s/values/values-ai.yaml
```

검증:
```bash
kubectl get pod -n dev -o wide
```

Expected: streaming-service, ai-service Pod 가 새 노드에 떠 있음.

- [ ] **Step 5: commit**

```bash
git add k8s/charts/microservice/templates/deployment.yaml k8s/values/values-streaming.yaml k8s/values/values-ai.yaml
git commit -m "feat(k3s): nodeSelector 추가 + streaming/ai 별도 노드 분리"
```

---

## Summary

본 plan 완료 시 결과물:

- ✅ AWS EC2 t3.large 1대 위에 K3s v1.30 단일 노드 클러스터
- ✅ 9개 Spring Boot 마이크로서비스 Helm 배포 (단일 차트 + 9 values)
- ✅ in-cluster PostgreSQL 16 (8 DBs) + 매일 02:00 S3 백업
- ✅ in-cluster Redis (Bitnami)
- ✅ Traefik Ingress (K3s 내장) + Elastic IP 외부 노출
- ✅ AWS S3 + IAM User Access Key 영상/이미지 저장
- ✅ 외부 Kafka (52.78.88.98:9092) 연동 유지
- ✅ GitHub Actions cd.yml 의 deploy-to-ec2 → deploy-to-k3s 교체
- ✅ EC2 호스트 logrotate + 주간 docker prune
- ✅ K3s 운영 README + 팀 공유 1페이지 요약 갱신

(선택) 도메인 + Let's Encrypt SSL · 별도 EC2 분리 폴백.

---

## Test plan (수동 검증)

각 Phase 완료 시 검증:

| Phase | 검증 명령 | 통과 기준 |
|---|---|---|
| 1 | `kubectl get nodes` | Ready 1개 |
| 2 | `kubectl get all,pvc,secret -n dev` | postgres + redis Running, PVC Bound, 5 Secret |
| 3 | `helm lint k8s/charts/microservice` | 0 failed |
| 4 | `for svc in ...; helm template -f values-$svc.yaml` | 9개 모두 OK |
| 5 | `kubectl logs -l app.kubernetes.io/instance=movie-service` | "Started MovieServiceApplication" |
| 6 | `kubectl get pod -n dev` | 9개 모두 Running. `curl http://<EIP>/actuator/health` → 200 |
| 7 | GitHub Actions 탭에서 `cd.yml` 의 deploy-to-k3s job | success. 이미지 tag 가 commit sha |
| 8 | `kubectl create job pg-backup-manual --from=cronjob/pg-backup -n dev` | Completed + S3 에 dump 파일 |
| 9 (선택) | `curl https://<도메인>/actuator/health` | 200 + 유효 SSL |
| 10 (선택) | `kubectl get pod -n dev -o wide` | streaming/ai 가 새 노드에 |

---

## Risk

| 위험 | 확률 | 영향 | 완화책 (plan 내 위치) |
|---|---|---|---|
| 9개 동시 OOM | 높음 | 매우 높음 | Task 6.2~6.4 단계별 메모리 체크, swap 8GB (Task 1.4 Step 3), §1.5 폴백 (spec) |
| AWS Key 권한 부족 (S3 미허용) | 중 | 높음 | Task 1.6 에서 정책 명시. 부족 시 IAM 정책 보강 |
| Postgres 데이터 손실 | 중 | 매우 높음 | Task 8.1 일일 S3 백업 + 수동 즉시 검증 |
| GitHub Actions SSH 실패 | 중 | 중 | Task 7.3 timeout 5m + appleboy/ssh-action 검증된 액션 사용 |
| EC2 stop 잊고 비용 누적 | 중 | 낮 | Task 8.3 README 에 비용 정리 명령 명시 |
| ai-service / streaming-service Docker 이미지 미존재 | 중 | 중 | Task 5.1 Step 1 에서 이미지 존재 확인. 없으면 Step 2 로 빌드/푸시 |

---

## Spec coverage

본 plan 이 spec 의 각 섹션을 어떻게 구현하는지 매핑:

| Spec 섹션 | Plan Task |
|---|---|
| §1.4 스코프 (활성 9개 서비스) | Phase 4 (values 9개) + Phase 5-6 (배포) |
| §1.5 OOM 폴백 시나리오 | Task 1.4 Step 3 (swap), Task 6.2~6.4 (단계별 메모리 체크), Phase 10 (분리 폴백) |
| §2.1 메모리 분배 | values 의 resources.limits 차등 (Task 4.1~4.9) |
| §2.2 디스크 분배 | Task 8.2 (logrotate + prune) |
| §3 Q1 K3s 단일 노드 | Task 1.4 (K3s 설치) |
| §3 Q2 ap-northeast-2 | Task 1.1 Step 1 |
| §3 Q3 in-cluster Postgres + S3 백업 | Task 2.2 + Task 8.1 |
| §3 Q4 Redis (Bitnami) | Task 2.3 |
| §3 Q5 ES OFF | Task 4.3 (values-movie) |
| §3 Q6 외부 Kafka | values 모든 파일의 KAFKA_BOOTSTRAP_SERVERS |
| §3 Q7 Helm 단일 차트 | Phase 3 |
| §3 Q8 GitHub Actions + SSH + helm | Phase 7 |
| §3 Q9 Traefik + EIP | Task 4.9 (gateway Ingress) + Task 6.4 (외부 검증) |
| §3 Q10 일단 HTTP, 도메인 후 SSL | Phase 9 (선택) |
| §3 Q11 K8s Secret 직접 | Task 2.4 |
| §3 Q12 IAM User Access Key | Task 1.6 + Task 4.2 (user) + Task 4.7 (streaming) |
| §4.1 아키텍처 다이어그램 | Phase 1-6 전체로 구현 |
| §4.3 보안그룹 | Task 1.1 Step 4 |
| §5 디렉토리 구조 | File Structure 섹션 + Phase 3-4 |
| §6 CI/CD | Phase 7 |
| §7 AWS 인프라 사전 준비 | Phase 1 전체 |
| §10 위험 요소 | Risk 섹션 + 각 Task 의 트러블슈팅 step |
| §11 미해결 / 미래 작업 | Phase 9-10 (선택) + README (Task 8.3) |

---

## Placeholder scan

본 plan 은 다음 항목에 대해 placeholder 가 아닌 실제 값/명령을 제시:
- ✅ 모든 YAML 매니페스트 완전 작성 (TBD/TODO 없음)
- ✅ 모든 kubectl/helm/git 명령에 정확한 인자
- ✅ 모든 환경변수 이름이 docker-compose.yml 과 일치 (JWT_PRIVATE_KEY, TOSS_PAYMENT_*, AWS_S3_BUCKET 등)
- ⚠️ 다음은 본인이 채워야 하는 값 (placeholder 명시):
  - EIP 주소 (예: `13.125.xxx.xxx`)
  - Docker Hub 사용자명 (예: `y0000h`)
  - S3 버킷명 (예: `beadv5-uploads-1`) — 본인 것으로 교체
  - JWT 키 / Toss 키 / OpenAI 키 — `.env.k3s` 에 본인 실제 값
  - 도메인 (Phase 9, 선택)

---

## Type consistency

본 plan 은 다음 일관성을 유지:
- Helm 차트 함수: `microservice.fullname`, `microservice.labels`, `microservice.selectorLabels` (Task 3.2 정의 → Task 3.3~3.7 호출)
- Secret 키 이름: docker-compose .env 와 100% 일치 (Task 2.4 + Task 4.1~4.9)
- Service DNS: `<service>.dev.svc.cluster.local:<port>` 패턴 통일
- Image repository: `y0000h/<service-name>:<tag>` 통일
- 메모리 limit: 일반 7개 = 512~768Mi, streaming/ai = 1Gi (spec §2.3 그대로)
