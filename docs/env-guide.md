# 환경 변수 가이드

운영 서버(`EC2`) 초기 세팅 또는 로컬 개발 환경 구성 시 필요한 모든 환경 변수 목록입니다.

- **운영**: `docker-compose.yml`의 `environment` 블록이 이 변수들을 컨테이너에 주입합니다.  
- **로컬**: `.env.example`을 복사해 `.env` 파일을 만든 뒤 값을 입력하세요.

```bash
cp .env.example .env
```

> ⚠️ `.env` 파일은 절대 Git에 커밋하지 마세요.

---

## 목차

1. [공통 — 데이터베이스 (PostgreSQL)](#1-공통--데이터베이스-postgresql)
2. [공통 — Kafka](#2-공통--kafka)
3. [공통 — Redis](#3-공통--redis)
4. [공통 — Spring 설정](#4-공통--spring-설정)
5. [서비스 포트](#5-서비스-포트)
6. [JWT — 인증 키](#6-jwt--인증-키)
7. [MinIO / S3 — 파일 저장소 (user-service)](#7-minio--s3--파일-저장소-user-service)
8. [Toss Payments — 결제 (payment-service)](#8-toss-payments--결제-payment-service)
9. [Settlement — 정산 설정 (settlement-service)](#9-settlement--정산-설정-settlement-service)
10. [Docker Hub — CI/CD 이미지 빌드](#10-docker-hub--cicd-이미지-빌드)

---

## 1. 공통 — 데이터베이스 (PostgreSQL)

> 적용 서비스: creator, payment, settlement, ticket, user, reivew, movie

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `DB_HOST` | ✅ | — | PostgreSQL 서버 호스트 (IP 또는 도메인) |
| `DB_USERNAME` | ✅ | — | DB 접속 계정 |
| `DB_PASSWORD` | ✅ | — | DB 접속 비밀번호 |

**참고**: 각 서비스는 별도 DB를 사용합니다 (`creator_db`, `payment_db`, `settlement_db`, `ticket_db`, `user_db`, `review_db`, `movie_db`).  
DB와 테이블은 `init.sql`로 사전 생성해야 합니다.

```bash
# 예시
DB_HOST=10.0.1.100
DB_USERNAME=cinemaCICD
DB_PASSWORD=strong_password_here
```

---

## 2. 공통 — Kafka

> 적용 서비스: 전체 (gateway 포함)

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | ✅ | `52.78.88.98:9092` | Kafka 브로커 주소. 여러 개일 경우 쉼표 구분 |

```bash
# 예시 (단일 브로커)
KAFKA_BOOTSTRAP_SERVERS=52.78.88.98:9092

# 예시 (복수 브로커)
KAFKA_BOOTSTRAP_SERVERS=broker1:9092,broker2:9092
```

---

## 3. 공통 — Redis

> 적용 서비스: creator, ticket, user

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `REDIS_HOST` | ✅ | — | Redis 서버 호스트 |
| `REDIS_PORT` | ❌ | `6379` | Redis 포트 |

```bash
REDIS_HOST=10.0.1.200
REDIS_PORT=6379
```

---

## 4. 공통 — Spring 설정

> 적용 서비스: 전체

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | ✅ | `dev` | 활성 프로파일. **운영 서버에서는 반드시 `prod`로 설정** |
| `DDL_AUTO` | ❌ | `validate` | JPA `ddl-auto` 정책. 아래 값 중 선택 |

**`DDL_AUTO` 값 선택 기준:**

| 값 | 사용 시점 | 설명 |
|----|----------|------|
| `validate` | 운영 (기본값) | 스키마와 엔티티 불일치 시 기동 실패 — 스키마 자동 변경 없음 |
| `update` | 개발 환경 | 엔티티 변경사항을 DB에 자동 반영 (컬럼 추가 등) |
| `create` | 최초 구축 시 | 테이블을 새로 생성 (**기존 데이터 삭제됨 — 주의**) |
| `create-drop` | 테스트 | 기동 시 생성, 종료 시 삭제 |

> ⚠️ **신규 서버 초기 구축 절차**:
> 1. `DDL_AUTO=create`로 설정 → 서비스 기동 → 테이블 자동 생성
> 2. `DDL_AUTO=validate`로 변경 → 재기동

---

## 5. 서비스 포트

| 변수 | 필수 | 기본값 | 서비스 |
|------|------|--------|--------|
| `GATEWAY_PORT` | ❌ | `8000` | gateway-service |
| `CREATOR_PORT` | ❌ | `8080` | creator-service |
| `PAYMENT_PORT` | ❌ | `8081` | payment-service |
| `SETTLEMENT_PORT` | ❌ | `8083` | settlement-service |
| `TICKET_PORT` | ❌ | `8084` | ticket-service |
| `USER_PORT` | ❌ | `8085` | user-service |
| `MOVIE_PORT` | ❌ | `8086` | movie-service |
| `REVIEW_PORT` | ❌ | `8087` | reivew-service |

포트를 변경할 경우 EC2 보안 그룹(inbound 규칙)도 함께 수정해야 합니다.

---

## 6. JWT — 인증 키

RSA 키페어를 사용합니다. creator-service / user-service에서 토큰을 **발급**하고, gateway-service에서 **검증**합니다.

> 적용 서비스: gateway (검증), creator / user (발급)

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `JWT_TOKEN_PUBLIC` | ✅ | — | **gateway 전용** RSA 공개키. 서명 검증에만 사용 |
| `JWT_PRIVATE_KEY` | ✅ | — | RSA 개인키. 토큰 서명에 사용 (creator, user) |
| `JWT_PUBLIC_KEY` | ✅ | — | RSA 공개키. 토큰 검증에 사용 (creator, user) |
| `JWT_ACCESS_TOKEN_EXPIRY` | ❌ | `3600` | 액세스 토큰 만료시간 (초). 기본 1시간 |
| `JWT_REFRESH_TOKEN_EXPIRY` | ❌ | `604800` | 리프레시 토큰 만료시간 (초). 기본 7일 |

**RSA 키 생성 방법:**

```bash
# 개인키 생성
openssl genrsa -out private_key.pem 2048

# 공개키 추출
openssl rsa -in private_key.pem -pubout -out public_key.pem

# Base64로 인코딩해서 환경 변수에 입력
JWT_PRIVATE_KEY=$(base64 -w 0 private_key.pem)
JWT_PUBLIC_KEY=$(base64 -w 0 public_key.pem)
JWT_TOKEN_PUBLIC=$(base64 -w 0 public_key.pem)  # gateway는 공개키만 필요
```

> ⚠️ `JWT_TOKEN_PUBLIC`과 `JWT_PUBLIC_KEY`는 동일한 공개키입니다. 변수명이 다를 뿐입니다.

---

## 7. MinIO / S3 — 파일 저장소 (user-service)

> 적용 서비스: user-service

AWS SDK를 사용하며 MinIO(S3 호환)와 AWS S3 모두 지원합니다.

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `MINIO_ENDPOINT` | ✅ | — | 스토리지 엔드포인트 URL. AWS S3는 빈값으로 두면 기본 AWS 엔드포인트 사용 |
| `MINIO_ACCESS_KEY` | ✅ | — | 액세스 키 (AWS: Access Key ID) |
| `MINIO_SECRET_KEY` | ✅ | — | 시크릿 키 (AWS: Secret Access Key) |
| `MINIO_BUCKET` | ✅ | — | 버킷 이름 |

```bash
# MinIO 로컬 사용 예시
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=cinema-bucket

# AWS S3 사용 예시
MINIO_ENDPOINT=
MINIO_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
MINIO_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
MINIO_BUCKET=cinema-prod-bucket
```

---

## 8. Toss Payments — 결제 (payment-service)

> 적용 서비스: payment-service

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `TOSS_PAYMENT_SECRET` | ✅ | — | 시크릿 키. **서버에서만 사용, 절대 클라이언트에 노출 금지** |
| `TOSS_PAYMENT_CK` | ✅ | — | 클라이언트 키. 프론트엔드 연동용 |

- 키 발급: [토스페이먼츠 개발자센터](https://developers.tosspayments.com)
- 테스트 키: `test_` 접두사 / 운영 키: `live_` 접두사

```bash
# 테스트 환경
TOSS_PAYMENT_SECRET=test_sk_xxxxxxxxxxxx
TOSS_PAYMENT_CK=test_ck_xxxxxxxxxxxx

# 운영 환경
TOSS_PAYMENT_SECRET=live_sk_xxxxxxxxxxxx
TOSS_PAYMENT_CK=live_ck_xxxxxxxxxxxx
```

---

## 9. Settlement — 정산 설정 (settlement-service)

> 적용 서비스: settlement-service

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `SETTLEMENT_FEE_RATE` | ❌ | `0.10` | 플랫폼 수수료율 (0.10 = 10%) |
| `SETTLEMENT_COOKIE_TO_KRW_RATE` | ❌ | `100` | 쿠키 → 원화 환산 비율 (1쿠키 = 100원) |
| `SETTLEMENT_DLQ_FALLBACK_PATH` | ❌ | `/tmp/settlement-dlq-fallback.jsonl` | Kafka 소비 실패 시 로컬 fallback 파일 경로 |
| `SETTLEMENT_DLQ_MAX_RETRY` | ❌ | `3` | DLQ 재시도 최대 횟수 |

> 기본값이 설정되어 있으므로 변경이 필요할 때만 `.env`에 추가하면 됩니다.

---

## 10. Docker Hub — CI/CD 이미지 빌드

> CD 파이프라인(`.github/workflows/cd.yml`)에서 사용. **GitHub Actions Secrets에 등록**

| 변수 | 필수 | 설명 |
|------|------|------|
| `DOCKERHUB_USERNAME` | ✅ | Docker Hub 계정명. 이미지명 접두사로 사용 (`{USERNAME}/{service}:tag`) |
| `DOCKERHUB_TOKEN` | ✅ | Docker Hub Access Token (비밀번호 대신 사용) |
| `EC2_HOST` | ✅ | 배포 대상 EC2 인스턴스 IP 또는 도메인 |
| `EC2_USER` | ✅ | EC2 SSH 접속 계정 (예: `ubuntu`, `ec2-user`) |
| `EC2_SSH_KEY` | ✅ | EC2 SSH 개인키 (PEM 파일 내용 전체) |

> Docker Hub Token 발급: [Docker Hub → Account Settings → Security → New Access Token](https://hub.docker.com/settings/security)

---

## 전체 환경 변수 템플릿

아래 내용을 복사해 EC2 서버의 `~/app/.env` 파일로 저장한 뒤, `# TODO` 항목을 채워 넣으세요.

```bash
# ============================================================
# cinemaCICD 운영 환경 변수 템플릿
# 위치: ~/app/.env  (docker-compose.yml과 같은 디렉터리)
# ============================================================

# ──────────────────────────────────────────
# [DOCKER HUB]
# CD 파이프라인이 이미지를 push/pull할 때 사용
# GitHub Actions Secrets에도 동일하게 등록 필요
# ──────────────────────────────────────────
DOCKERHUB_USERNAME=         # TODO: Docker Hub 계정명

# ──────────────────────────────────────────
# [DATABASE] PostgreSQL
# 모든 서비스 공통 — 서비스별로 DB를 분리하여 사용
# (creator_db / payment_db / settlement_db / ticket_db / user_db / review_db / movie_db)
# ──────────────────────────────────────────
DB_HOST=                    # TODO: PostgreSQL 서버 IP 또는 도메인 (예: 10.0.1.100)
DB_USERNAME=                # TODO: DB 접속 계정
DB_PASSWORD=                # TODO: DB 접속 비밀번호

# ──────────────────────────────────────────
# [KAFKA]
# 모든 서비스 공통
# ──────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=52.78.88.98:9092   # 브로커 변경 시 수정

# ──────────────────────────────────────────
# [REDIS]
# creator-service, ticket-service, user-service 사용
# ──────────────────────────────────────────
REDIS_HOST=                 # TODO: Redis 서버 IP 또는 도메인
REDIS_PORT=6379

# ──────────────────────────────────────────
# [JWT] RSA 키페어
# - JWT_PRIVATE_KEY : creator-service, user-service 토큰 발급
# - JWT_PUBLIC_KEY  : creator-service, user-service 토큰 검증
# - JWT_TOKEN_PUBLIC: gateway-service 서명 검증 전용
# 생성: openssl genrsa -out private.pem 2048 && openssl rsa -in private.pem -pubout -out public.pem
# ──────────────────────────────────────────
JWT_PRIVATE_KEY=            # TODO: RSA 개인키 (Base64 인코딩)
JWT_PUBLIC_KEY=             # TODO: RSA 공개키 (Base64 인코딩)
JWT_TOKEN_PUBLIC=           # TODO: RSA 공개키 (JWT_PUBLIC_KEY와 동일한 값)
JWT_ACCESS_TOKEN_EXPIRY=3600      # 액세스 토큰 만료 (초), 기본 1시간
JWT_REFRESH_TOKEN_EXPIRY=604800   # 리프레시 토큰 만료 (초), 기본 7일

# ──────────────────────────────────────────
# [MINIO / S3] 파일 저장소
# user-service 전용
# AWS S3 사용 시 MINIO_ENDPOINT 비워두면 기본 AWS 엔드포인트 사용
# ──────────────────────────────────────────
MINIO_ENDPOINT=             # TODO: 엔드포인트 URL (MinIO: http://..., AWS S3: 비워둠)
MINIO_ACCESS_KEY=           # TODO: 액세스 키 (AWS: Access Key ID)
MINIO_SECRET_KEY=           # TODO: 시크릿 키 (AWS: Secret Access Key)
MINIO_BUCKET=               # TODO: 버킷 이름

# ──────────────────────────────────────────
# [TOSS PAYMENTS] 결제
# payment-service 전용
# 발급: https://developers.tosspayments.com
# 테스트 키: test_ 접두사 / 운영 키: live_ 접두사
# ──────────────────────────────────────────
TOSS_PAYMENT_SECRET=        # TODO: 시크릿 키 (서버 전용, 절대 외부 노출 금지)
TOSS_PAYMENT_CK=            # TODO: 클라이언트 키 (프론트엔드 연동용)

# ──────────────────────────────────────────
# [SETTLEMENT] 정산 설정
# settlement-service 전용 — 기본값 그대로 사용해도 무방
# ──────────────────────────────────────────
SETTLEMENT_FEE_RATE=0.10                                    # 플랫폼 수수료율 (10%)
SETTLEMENT_COOKIE_TO_KRW_RATE=100                           # 1쿠키 = 100원
SETTLEMENT_DLQ_FALLBACK_PATH=/tmp/settlement-dlq-fallback.jsonl
SETTLEMENT_DLQ_MAX_RETRY=3

# ──────────────────────────────────────────
# [SPRING] 프로파일 및 JPA DDL 정책
# 운영 서버에서는 반드시 아래 값 그대로 유지
# DDL_AUTO 초기 구축 시에만 create로 변경 후 validate로 복원
# ──────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod
DDL_AUTO=validate           # validate(운영) | update(개발) | create(최초 구축)

# ──────────────────────────────────────────
# [서비스 포트] 기본값에서 변경 불필요 시 그대로 유지
# 변경 시 EC2 보안 그룹 inbound 규칙도 함께 수정
# ──────────────────────────────────────────
GATEWAY_PORT=8000
CREATOR_PORT=8080
PAYMENT_PORT=8081
SETTLEMENT_PORT=8083
TICKET_PORT=8084
USER_PORT=8085
MOVIE_PORT=8086
REVIEW_PORT=8087
```

---

## EC2 초기 세팅 체크리스트

운영 서버에 처음 배포할 때 아래 순서를 따르세요.

```bash
# 1. .env 파일 생성
cp .env.example .env
vi .env   # 모든 필수(✅) 변수 입력

# 2. 최초 배포 시 DDL_AUTO를 create로 설정 (테이블 자동 생성)
#    .env 파일에서 DDL_AUTO=create 로 변경

# 3. 서비스 기동
docker compose --profile {service} up -d

# 4. 로그 확인 (테이블 생성 완료 여부)
docker compose logs {service} --tail=50

# 5. DDL_AUTO를 validate로 복원
#    .env 파일에서 DDL_AUTO=validate 로 변경

# 6. 서비스 재시작
docker compose --profile {service} up -d
```