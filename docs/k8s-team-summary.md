# 쿠버네티스(K3s on EC2) 배포 — 팀 공유용 1페이지 요약

> **TL;DR**: 지금 EC2 1대에 docker-compose 로 서비스 띄우는 걸 → 같은 EC2 1대 위에 **K3s(경량 쿠버네티스) + Helm** 으로 옮긴다. 추가 비용 거의 0, CI/CD 는 95% 그대로 유지, 마지막 배포 step 만 `helm upgrade` 로 교체. 매니페스트는 100% 표준 K8s 호환이라 나중에 EKS로 이전해도 그대로 재사용 가능.

**담당**: y0000h2 / **브랜치**: `feature/k3s-deployment`

---

## 🎯 왜 K3s로?

| 지금 (EC2 docker-compose) | 옮긴 후 (K3s on 같은 EC2) |
|---|---|
| EC2 1대에 9개 서비스 직접 실행 → 한 놈 죽으면 수동 재시작 | K3s 가 Pod 죽으면 자동 재시작 (self-healing) |
| 배포 = `docker compose up -d` (다운타임 발생) | 무중단 롤링 업데이트 (Deployment) |
| 환경변수·시크릿 관리가 `.env` 파일로 뒤죽박죽 | ConfigMap / Secret 으로 분리 관리 |
| K8s 경험/포트폴리오 없음 | **표준 매니페스트 = 향후 EKS/GKE 이전 제로 코스트** |

→ **MSA 9개 서비스에 K8s 패러다임 적용.** 풀버전 EKS 가 이상적이지만 자원 한계로 K3s(경량 배포판) 선택.

---

## 📦 K3s vs 풀버전 K8s (왜 K3s 인가)

| 항목 | 풀버전 K8s / EKS | **K3s** |
|---|---|---|
| 컨트롤플레인 메모리 | 1.5~2 GB | **~500 MB** |
| 설치 | kubeadm + CNI + Ingress 반나절 | **`curl ...sh`** 한 줄 (1분) |
| 매니페스트 호환성 | — | **100%** (Deployment/Service/Ingress/Helm 동일) |
| 8GB EC2 위에서 | OOM 위험 큼 | 빠듯하지만 가능 |
| 학습/포트폴리오 가치 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (동일) |

→ **차트·매니페스트 그대로 EKS/EC2 클러스터로 이전 가능**. `values-*.yaml` 만 환경별로 갈아끼우면 끝.

---

## 🏗️ 아키텍처 한 장

```
사용자 → Vercel(프론트) → Elastic IP:80 → Traefik(K3s 내장 Ingress) → gateway-service Pod
                                                                            ↓
                                          ┌─────────────────────────────────┴────────────────┐
                                          ↓                                                  ↓
           [user(+review) / creator / movie(ES OFF) / payment / ticket / settlement]   [streaming / ai]
                                          ↓                                                  ↓
                  ┌───────────────────────┼───────────────────────┐                          │
                  ↓                       ↓                       ↓                          ↓
       [in-cluster PostgreSQL]   [in-cluster Redis]      [외부 Kafka EC2]                  [AWS S3]
       (PVC + EBS, 9개 DB)       (Bitnami Helm)          (52.78.88.98:9092 팀 공용)       (영상/HLS/프로필)
              ↓                                                                             ↑
       [pg_dump → S3 매일 02:00]                                                ai-service → OpenAI API
```

**모든 구성요소가 EC2 t3.large 1대 안에 돌아감**. S3/Kafka 만 외부.

---

## 📌 핵심 결정 12가지

| # | 결정 항목 | 선택 | 한 줄 이유 |
|---|---|---|---|
| 1 | **어디에?** | K3s on EC2 t3.large 1대 | 받은 자원이 1대 뿐. 매니페스트는 100% K8s 호환 |
| 2 | **리전?** | ap-northeast-2 (서울) | 한국 사용자 시연 → 지연 최소 |
| 3 | **DB?** | in-cluster PostgreSQL (PVC + EBS) + S3 일일 백업 | RDS 자원 미수령. 매일 `pg_dump` → S3 로 보호 |
| 4 | **Redis?** | in-cluster (Bitnami Helm) | ElastiCache 비용 회피. 150 MB 로 충분 |
| 5 | **Elasticsearch?** | **OFF** (`MOVIE_ES_ENABLED=false`, JPA 폴백) | 메모리 한계. PR #133 폴백 구조 그대로 활용 |
| 6 | **Kafka?** | 외부 EC2 그대로 (`52.78.88.98:9092`) | 팀 공용. 옮길 이유 없음 |
| 7 | **매니페스트?** | Helm 단일 차트 + 서비스별 `values-*.yaml` 9개 | 쌍둥이 서비스에 최적 |
| 8 | **배포?** | GitHub Actions → SSH(EIP) → `helm upgrade` | EKS 의 `eks update-kubeconfig` 불필요, SSH 단순 |
| 9 | **외부 노출?** | Traefik(K3s 내장) + Elastic IP | ALB 비용/권한 부재. 별도 설치 불필요 |
| 10 | **HTTPS?** | 일단 HTTP + EIP → 도메인 결정 후 cert-manager + Let's Encrypt | 도메인 미정. 시연 HTTP 허용 |
| 11 | **Secret?** | K8s Secret 직접 (`kubectl create secret`) | ESO + Secrets Manager 는 단일 노드에 과도 |
| 12 | **S3 인증?** | IAM User Access Key + K8s Secret 주입 | IRSA(IAM-K8s 통합) K3s 에 없음 |

---

## 💰 예상 비용 (시연 기간 월간)

| 항목 | 월 비용 | 본인 부담 |
|---|---|---|
| EC2 t3.large (24/7 가정) | ~$60 | **$0 (받은 자원)** |
| EBS gp3 50GB | ~$4 | **$0 (받은 자원)** |
| Elastic IP (attach 상태) | $0 | $0 |
| S3 (10GB 가정) | <$1 | **본인 부담** |
| 데이터 전송 (첫 100GB) | ~$5 | **본인 부담** |
| **합계 본인 추가 부담** | | **약 $0~$6/월** |

**시연 후 정리**: EC2 stop → 시간당 과금 즉시 멈춤. 완전 정리 시 EC2 terminate + EBS delete + S3 비우기.

---

## ⚠️ OOM 위험 사전 고지 (반드시 인지)

9개 서비스 + Postgres + Redis + K3s 컨트롤플레인을 8GB 안에 모두 올리는 구성은 **상시 OOM 위험권**입니다.

**메모리 이론치**: 약 7.7 ~ 8.7 GB / 8 GB ← **한계 초과 가능**
→ **swap 4~8 GB 추가는 필수 (선택 아님)**.
→ **폴백 시나리오는 사전 합의**되어 있음 (아래 표).

### 서비스별 메모리 차등 정책

| 서비스 | mem limit | JVM heap |
|---|---|---|
| 일반 7개 (gateway/user/creator/movie/payment/ticket/settlement) | **512Mi** | `-Xmx256m` |
| streaming-service (HLS 변환 부하) | **1Gi** | `-Xmx512m` |
| ai-service (OpenAI 호출 + 임베딩) | **1Gi** | `-Xmx512m` |

---

## 🔄 폴백 시나리오 (사전 합의)

| 신호 | 1차 대응 | 2차 대응 |
|---|---|---|
| 노드 메모리 90% 초과 / Pod OOMKilled | swap 사용 확인 + JVM heap 더 낮춤 | 가장 부하 적은 서비스 1개 `replicas: 0` |
| ai-service OpenAI 동시 호출 폭증 | webclient timeout + backpressure | ai-service `replicas: 0` 임시 중단 → 자원 추가 협상 |
| streaming-service HLS 변환 OOM | mem limit 1.5Gi 상향 | HLS 처리만 별도 EC2 분리 |
| EBS 50GB 부족 | `docker system prune -af` + 로그 truncate | (운영팀과 EBS 증설 협상) |
| PostgreSQL 데이터 손상 | S3 백업에서 `pg_restore` (최대 7일 데이터) | — |
| K3s 자체 장애 | EC2 재부팅 → K3s systemd 자동 복구 | — |
| 다 올렸는데 안정 운영 불가 | 시연용 시나리오 서비스만 가동 | docker-compose 회귀 (compose 파일 보존) |

---

## 🛠️ 변경되는 것 / 그대로인 것

### 변경되는 것
- `.github/workflows/cd.yml` — 마지막 deploy step (SCP+SSH → `SSH + helm upgrade`)
- `gateway-service/src/main/resources/application-prod.yaml` — 라우팅 URI 형식 (`BASE_IP+PORT` → `SERVICE_HOST` 단일 변수), **review → user-service 로 통합**
- 신규 디렉토리: `k8s/` (Helm 차트 + values 9개 + infra 스크립트 + aws 가이드)

### 그대로인 것
- 9개 서비스의 **비즈니스 로직 코드 — 건드리지 않음**
- `docker-compose.yml` — 로컬 개발용으로 계속 사용
- `application-{prod,dev}.yaml` 의 spring.* 설정 대부분
- CI 워크플로우 (PR 빌드/테스트)
- 외부 Kafka (`52.78.88.98:9092`)
- Docker 이미지 이름 (`y0000h/{service}:` 유지)

---

## ⏱️ 진행 일정 (Phase별)

| Phase | 단계 | 예상 소요 |
|---|---|---|
| Phase 0 | 브랜치 + k8s/ 골격 | 5분 |
| Phase 1 | AWS 인프라 셋업 (VPC/EC2/EIP/SG/S3/IAM) | 1~2시간 |
| Phase 2 | K3s 인프라 (namespace/Postgres/Redis/Secret) | 1시간 |
| Phase 3 | Helm 차트 작성 | 2시간 |
| Phase 4 | values 9개 작성 | 2.5시간 |
| Phase 5 | movie-service 시범 배포 | 1시간 |
| Phase 6 | 나머지 8개 + gateway 외부 노출 | 3시간 |
| Phase 7 | CI/CD 교체 | 1시간 |
| Phase 8 | 운영 자동화 (백업 + 로그) | 30분 |
| Phase 9 (선택) | 도메인 + Let's Encrypt | — |
| Phase 10 (선택) | ai/streaming 별도 EC2 분리 | — |

**총 예상 작업: 12~14시간** (강의 + 디버깅 + 조원 대기 포함 1~2주)

---

## 🙋 조원이 알아야 할 것

- **로컬 개발 방식 그대로**: docker-compose 로 띄워도 OK
- **review-service 는 user-service 안으로 통합됨** (gateway 라우팅도 `/api/review/**` → user-service)
- **본인 서비스에 application-prod.yaml 환경변수 추가 시 알려주기** → `k8s/values/values-{본인서비스}.yaml` 에 반영 필요
- **시크릿(DB pw, JWT, Toss 키, OpenAI 키 등) 추가 시 알려주기** → `kubectl create secret generic ...` 으로 클러스터에 등록해줌 (GitHub Secrets 아님)
- **Docker 이미지 이름 그대로** (`y0000h/{service}:`)
- **본인 서비스 메모리 폭주 주의**: streaming/ai 외에는 mem limit 512Mi. Pod 가 OOMKilled 되면 slack 으로 바로 공유

---

## 🔗 자세한 자료

- **설계서 (왜/무엇)**: [docs/superpowers/specs/2026-04-24-k3s-ec2-deployment-design.md](superpowers/specs/2026-04-24-k3s-ec2-deployment-design.md)
- **구현 계획서 (어떻게/순서, Phase 0~10)**: [docs/superpowers/plans/2026-04-24-k3s-ec2-deployment.md](superpowers/plans/2026-04-24-k3s-ec2-deployment.md)
- **궁금한 점은 슬랙으로** 또는 본 PR 댓글로

---

## ❓ 자주 받을 질문 (예상)

**Q. 왜 EKS 가 아니고 K3s 야?**
> 받은 AWS 자원이 EC2 1대 한정. EKS 컨트롤플레인($73/월) + 최소 노드 2대 구성 불가능. K3s 는 같은 EC2 안에 컨트롤플레인+워커를 한꺼번에 태우는 경량 배포판. 매니페스트는 100% K8s 표준이라 나중에 EKS로 옮겨도 차트 재사용 가능.

**Q. 그럼 풀 K8s 경험 포트폴리오 안 되는 거 아니야?**
> K3s 는 K8s CNCF 인증된 배포판이라 멘토에게 "K8s 경험 있음" 이라고 말해도 100% 참. Helm/Deployment/Service/Ingress 전부 동일. CLI(`kubectl`) 도 같음.

**Q. RDS 없는데 Postgres 죽으면 어떻게 해?**
> 매일 02:00 `pg_dump` → S3 업로드 CronJob 으로 7일 retention. 노드 죽어도 어제치까진 복구. 단 "오늘 오전 ~ 지금" 사이 데이터는 유실 가능 (RDS 대비 단점). 시연 용도엔 허용 수준.

**Q. Helm 이 뭐야?**
> K8s 매니페스트 템플릿 엔진. 9개 쌍둥이 서비스를 **1개 차트 + 9개 values 파일**로 처리. "붕어빵 틀 1개 + 속재료 9개" 로 생각하면 됨.

**Q. 메모리 정말 버틸 수 있어?**
> 빠듯함. 이론치 7.7~8.7GB / 한도 8GB. swap 8GB 추가로 방어. ai/streaming 2개는 1Gi limit, 나머지 7개는 512Mi limit 로 차등. OOM 발생 시 폴백 시나리오 사전 합의됨.

**Q. 내가 뭐 해야 해?**
> 일단 별 거 없음. 본인 서비스 환경변수·시크릿 추가 시 알려주기. review 관련 코드 작업 중이면 "user-service 쪽으로 합쳐진다" 만 인지하면 됨.

**Q. 시연 끝나면 어떻게 정리해?**
> EC2 stop → 시간당 과금 즉시 멈춤. 완전 정리 시 `terminate` + EBS/S3 삭제. 받은 자원이라 본인 추가 청구 없음.
