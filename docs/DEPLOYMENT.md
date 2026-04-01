# DEPLOYMENT.md

이 문서는 cinemaCICD 프로젝트의 배포 구조와 순서를 정리합니다.

---

## 전체 구조

```
EC2
├── ~/app/.env                  ← 환경변수 (직접 작성)
├── ~/app/docker-compose.yml    ← CD 파이프라인이 자동 전송
├── PostgreSQL                  ← EC2에서 직접 관리
├── Redis                       ← EC2에서 직접 관리
└── 서비스 컨테이너 (CD 자동 배포)
    ├── gateway-service   :8000
    ├── creator-service   :8080
    ├── payment-service   :8081
    ├── settlement-service:8083
    ├── ticket-service    :8084
    ├── user-service      :8085
    └── reivew-service    :8087
```

---

## 배포 순서

### 1단계: GitHub Secrets 등록 (최초 1회)

**저장소 → Settings → Secrets and variables → Actions**

| Secret | 설명 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 계정명 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| `EC2_HOST` | 배포 서버 IP 또는 도메인 |
| `EC2_USER` | SSH 접속 사용자명 (예: `ubuntu`) |
| `EC2_SSH_KEY` | SSH 프라이빗 키 전체 내용 (PEM) |

---

### 2단계: EC2 서버 초기 세팅 (최초 1회)

```bash
# Docker 공식 저장소 추가
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list

# Docker 설치
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 현재 유저를 docker 그룹에 추가 (sudo 없이 사용)
sudo usermod -aG docker $USER
newgrp docker

# 앱 디렉토리 생성
mkdir -p ~/app
```

---

### 3단계: EC2에서 PostgreSQL, Redis 실행 (최초 1회)

PostgreSQL과 Redis는 EC2에서 직접 관리한다.

```bash
# PostgreSQL 실행 후 서비스별 DB 생성
CREATE DATABASE creator_db;
CREATE DATABASE payment_db;
CREATE DATABASE settlement_db;
CREATE DATABASE ticket_db;
CREATE DATABASE user_db;
CREATE DATABASE review_db;
```

---

### 4단계: EC2에 .env 파일 작성 (최초 1회)

```bash
vi ~/app/.env
```

```dotenv
# Docker Hub
DOCKERHUB_USERNAME=

# Kafka (외부 AWS 브로커 — 별도 구축 불필요)
KAFKA_BOOTSTRAP_SERVERS=52.78.88.98:9092

# Redis (EC2에서 별도 관리)
REDIS_HOST=
REDIS_PORT=6379

# AWS S3 (user-service 파일 저장소)
AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=

# PostgreSQL (EC2에서 별도 관리)
DB_HOST=
DB_USERNAME=
DB_PASSWORD=

# 서비스 포트
GATEWAY_PORT=8000
CREATOR_PORT=8080
PAYMENT_PORT=8081
SETTLEMENT_PORT=8083
TICKET_PORT=8084
USER_PORT=8085
REVIEW_PORT=8087
```

---

### 5단계: 코드 push → 자동 배포

`main` 브랜치에 push하면 CD 파이프라인이 자동 실행된다.

```
[1] 변경 감지
    ├─ 이전 커밋 vs 현재 커밋에서 변경된 *-service/ 디렉토리 추출
    └─ 변경사항 없으면 이후 단계 전부 스킵

[2] Gradle 빌드 (변경된 서비스만, 병렬 실행)
    └─ ./gradlew build → JAR 생성

[3] Docker 이미지 빌드 & Docker Hub 푸시 (병렬 실행)
    ├─ 이미지 태그: {sha:0:7} + latest 두 개 동시 푸시
    └─ GitHub Actions 캐시 활용

[4] EC2 배포 (병렬 실행)
    ├─ docker-compose.yml → EC2 ~/app/ 으로 SCP 전송
    └─ SSH 접속 후:
         docker compose --profile {service} pull
         docker compose --profile {service} up -d --remove-orphans
         docker image prune -f

[5] 결과 요약
    └─ GitHub Actions Step Summary에 배포 결과 테이블 출력
```

---

## 브랜치 전략

```
feature/{service}/기능명
        │
        ▼ PR
dev/{service}          ← 서비스별 개발 통합 브랜치
        │
        ▼ PR
develop                ← 전체 서비스 통합 테스트
        │
        ▼ PR
main                   ← push 시 CD 파이프라인 자동 실행 (운영 배포)
```

> `main` 브랜치에는 직접 push 금지, PR을 통해서만 머지

---

## 서비스 의존성

| 서비스 | 포트 | 의존 |
|--------|------|------|
| gateway-service | 8000 | — |
| creator-service | 8080 | — |
| payment-service | 8081 | — |
| settlement-service | 8083 | → creator-service (HTTP) |
| ticket-service | 8084 | → user-service (HTTP) |
| user-service | 8085 | AWS S3 |
| reivew-service | 8087 | — |

> `settlement-service`는 `creator-service`, `ticket-service`는 `user-service`가 먼저 떠 있어야 한다.

```bash
# settlement 배포 전 creator 먼저 실행
docker compose --profile creator-service up -d
docker compose --profile settlement-service up -d

# ticket 배포 전 user 먼저 실행
docker compose --profile user-service up -d
docker compose --profile ticket-service up -d
```

---

## 핵심 포인트

- **배포 단위**: 변경된 서비스만 재배포, 나머지는 그대로 유지
- **DB / Redis**: EC2에서 직접 관리, docker-compose 외부에서 운영
- **파일 저장**: AWS S3 버킷 사용
- **이미지 태그**: `{sha:0:7}` (추적용) + `latest` (최신 참조용)
- **Kafka**: 외부 AWS 브로커 사용 — 별도 구축 불필요