# DEPLOYMENT.md

이 문서는 cinemaCICD 프로젝트의 배포에 필요한 사전 준비와 배포 흐름을 정리합니다.

---

## 사전 준비 (1회 설정)

### 1. GitHub Secrets 등록

**저장소 → Settings → Secrets and variables → Actions** 에서 아래 5개를 등록한다.

| Secret | 설명 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 계정명 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| `EC2_HOST` | 배포 서버 IP 또는 도메인 |
| `EC2_USER` | SSH 접속 사용자명 (예: `ubuntu`) |
| `EC2_SSH_KEY` | SSH 프라이빗 키 전체 내용 (PEM) |

### 2. EC2 서버 초기 세팅

```bash
# Docker 설치
sudo apt update
sudo apt install -y docker.io docker-compose-plugin

# 현재 유저를 docker 그룹에 추가 (sudo 없이 사용)
sudo usermod -aG docker $USER

# 앱 디렉토리 생성
mkdir -p ~/app
```

### 3. EC2의 .env 파일 작성

`.env.example`을 참고하여 `~/app/.env` 파일을 직접 작성한다.

```bash
vi ~/app/.env
```

```dotenv
# Docker Hub
DOCKERHUB_USERNAME=

# Kafka (외부 AWS 브로커 — 별도 구축 불필요)
KAFKA_BOOTSTRAP_SERVERS=

# Redis
REDIS_HOST=52.78.88.98
REDIS_PORT=6379

# AWS S3 (user-service 파일 저장소)
AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=

# PostgreSQL (공유 DB 컨테이너 — init.sql로 자동 생성)
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

## 배포 흐름

### 브랜치 전략

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

### CD 파이프라인 (`.github/workflows/cd.yml`)

`main` 브랜치에 push 발생 시 자동 실행.

```
[1] 변경 감지
    ├─ detect-changed-services-cicd.sh 실행
    ├─ 이전 커밋 vs 현재 커밋에서 변경된 *-service/ 디렉토리 추출
    └─ 변경사항 없으면 이후 단계 전부 스킵

[2] Gradle 빌드 (변경된 서비스만, 병렬 실행)
    └─ cd {service} && ./gradlew build → JAR 생성

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

> **최초 배포 시**: `db` 컨테이너가 처음 시작될 때 `init.sql`이 자동 실행되어 6개 DB가 생성됨.
> 이후 재배포에서는 `db-data` 볼륨이 유지되므로 init.sql은 다시 실행되지 않음.

---

### CI 파이프라인 (`.github/workflows/ci.yml`)

`main` 대상 PR 오픈/업데이트 시 자동 실행.

```
[1] 변경 감지 (PR base SHA 기준)
[2] Gradle 빌드 (변경된 서비스만, 병렬 실행)
    └─ 빌드 실패 시 PR 머지 불가
```

---

## 서비스 포트 및 의존성

| 서비스 | 포트 | 외부 의존 |
|--------|------|-----------|
| gateway-service | 8000 | — |
| creator-service | 8080 | — |
| payment-service | 8081 | — |
| settlement-service | 8083 | → creator-service (HTTP) |
| ticket-service | 8084 | → user-service (HTTP) |
| user-service | 8085 | AWS S3 (파일 저장) |
| reivew-service | 8087 | — |

> `settlement-service`와 `creator-service`, `ticket-service`와 `user-service`는 **쌍으로 함께 실행**되어야 한다.

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

- **배포 단위**: 변경된 서비스만 재배포, 나머지 서비스는 그대로 유지
- **DB 자동 생성**: profile 없는 `db` 컨테이너가 항상 먼저 시작되고, `init.sql`로 6개 DB를 최초 1회 자동 생성. 각 서비스는 `depends_on: condition: service_healthy`로 DB 준비 후 시작
- **파일 저장**: AWS S3 버킷 사용 (`AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_REGION`, `AWS_S3_BUCKET` 설정 필요)
- **이미지 태그**: `{sha:0:7}` (추적용) + `latest` (항상 최신 참조용)
- **Kafka**: 외부 AWS 브로커 사용 — 별도 구축 불필요