# 전체 아키텍처 및 설계 결정

> **프로젝트**: beadv5_5_3M_BE — 영화 스트리밍 MSA (9개 서비스)
> **목표**: docker-compose 단일 서버 → k3s 클러스터로 전환

---

## 1. 왜 전환하는가

### Before (docker-compose)

```
t3.large 1대
└── docker-compose
      ├── 앱 서비스 9개
      ├── Kafka + Zookeeper
      ├── Redis
      ├── PostgreSQL
      └── Elasticsearch (별도 k3s 노드로 분리 운영)
```

| 문제 | 영향 |
|---|---|
| 서비스가 죽으면 아무도 안 살려줌 | 직접 SSH 접속해서 수동 복구 |
| 배포 시 docker compose up -d | 그 순간 다운타임 발생 |
| 트래픽 몰려도 수동 대응 | 스케일링 불가 |
| 8GB에 전부 올려서 ES 추가 불가 | 검색 기능 미운영 |

### After (k3s)

| 해결 | k3s 기능 |
|---|---|
| 서비스 자동 재시작 | Self-healing (Reconciliation Loop) |
| 무중단 배포 | Rolling Update |
| 자동 스케일링 | HPA |
| ES 분리 운영 | t3.medium에 별도 배포 |

---

## 2. 왜 EKS 대신 k3s인가

```
EKS 검토 결과
├── 컨트롤 플레인 $73/월 추가 비용
├── IAM 권한 제약 (EC2 추가 생성 불가)
└── EC2 2대 할당 한도 초과

k3s 선택 이유
├── kubectl, Helm, YAML 전부 호환 (K8s 코드 기반 동일)
├── 컨트롤 플레인 메모리 0.8GB (K8s 1.3GB 대비 절감)
└── 기존 EC2 2대 위에 바로 설치 가능
```

---

## 3. EC2 역할 분리

```
t3.medium (ip-172-31-42-216) — k3s server
├── 컨트롤 플레인 (API 서버, 스케줄러)  0.8GB
└── Elasticsearch Pod                   2.0GB
    (-Xms1g -Xmx1g, nori 플러그인 포함)

t3.large (ip-172-31-29-32) — k3s agent
├── 앱 서비스 9개 (k3s Pod)
├── Redis (k3s Pod)
├── PostgreSQL (k3s Pod, pgvector/pgvector:pg18, PVC 20Gi)
└── Kafka (k3s Pod, KRaft 모드, PVC 10Gi)
```

### 왜 medium이 server인가

컨트롤 플레인과 워크로드를 분리하는 게 원칙에 맞음.
medium은 두뇌 역할 + ES만, large는 실제 서비스 실행.

---

## 4. 메모리 분석

```
t3.large (8GB)                              t3.medium (4GB)
┌──────────────────────────────────────┐    ┌──────────────────────────────────┐
│ 앱 9개 + Kafka(1GB) + Redis + PG(0.75GB) : 5.3GB  │    │ k3s server (컨트롤 플레인)       │
│ k3s agent 오버헤드       : 0.3GB     │    │              : 0.8GB             │
│ OS                       : 0.3GB     │    │ Elasticsearch: 2.0GB             │
│ ──────────────────────────────────── │    │ OS           : 0.3GB             │
│ 합계                     : ~5.9GB    │    │ ──────────────────────────────── │
│ 여유                     : 2.1GB     │    │ 합계         : ~3.1GB            │
└──────────────────────────────────────┘    │ 여유         : 0.9GB             │
                                            └──────────────────────────────────┘
```

---

## 5. 전체 아키텍처 그림

```
사용자
    ↓ HTTPS
[Vercel 프론트엔드]
    ↓
[t3.large — k3s agent (8GB)]
    │
    ├── Traefik (k3s 내장 Ingress)     ← 외부 트래픽 진입점
    │       ↓
    ├── gateway-service    (k3s Pod)
    ├── user-service       (k3s Pod)
    ├── creator-service    (k3s Pod)
    ├── movie-service      (k3s Pod)
    ├── payment-service    (k3s Pod)
    ├── ticket-service     (k3s Pod)
    ├── settlement-service (k3s Pod)
    ├── streaming-service  (k3s Pod)
    ├── ai-service         (k3s Pod)
    ├── Redis              (k3s Pod)
    ├── PostgreSQL         (k3s Pod, pgvector/pgvector:pg18, PVC 20Gi)
    └── Kafka              (k3s Pod, KRaft 모드, PVC 10Gi)

[t3.medium — k3s server (4GB)]
    ├── k3s 컨트롤 플레인 (API 서버, 스케줄러)
    └── Elasticsearch (k3s Pod, nori 플러그인)
```

---

## 6. 네트워크 흐름

| 경로 | 주소 |
|---|---|
| 사용자 API 요청 | Vercel → Traefik(80) → gateway-service → 내부 서비스 |
| 서비스 간 통신 | ClusterIP DNS (서비스명.dev.svc.cluster.local) |
| Kafka | 모든 서비스 → kafka.dev.svc.cluster.local:9092 |
| DB | 서비스 → postgresql.dev.svc.cluster.local:5432 |
| Redis | 서비스 → redis.dev.svc.cluster.local:6379 |
| ES | movie-service → elasticsearch.dev.svc.cluster.local:9200 |

---

## 7. DB 현황

| 서비스 | DB 이름 | 비고 |
|---|---|---|
| ai-service | ai_db | pgvector 0.8.2 활성화 |
| user-service | user_db | |
| creator-service | creator_db | |
| movie-service | creator_db | creator와 공유 |
| payment-service | payment_db | |
| ticket-service | ticket_db | |
| settlement-service | settlement_db | |
| streaming-service | streaming_db | |

PostgreSQL: `pgvector/pgvector:pg18` (k3s Pod 운영 중, DB 유저: `msauser`)
Elasticsearch: `9.0.0` (nori 플러그인 포함, t3.medium Pod 운영 중)

---

## 7-1. 왜 Traefik인가 (NodePort 대신)

Pod는 클러스터 내부 IP(10.42.x.x)만 가짐 → 외부에서 직접 접근 불가.
외부 트래픽을 받아서 내부 Pod로 연결해줄 진입점이 반드시 필요.

```
방법 1: NodePort (Traefik 없이 gateway 직접 노출)
├── 포트 번호 30000~32767 범위 강제 → 80/443 못 씀
├── http://ec2-ip:30080 처럼 포트 번호를 URL에 써야 함
├── SSL 처리를 gateway가 직접 해야 함
└── gateway Pod 재시작 시 순간 외부 접근 끊김

방법 2: Traefik (채택)
├── 80/443 표준 포트 → 사용자가 포트 번호 안 써도 됨
├── SSL 인증서 자동 갱신 → gateway가 신경 안 써도 됨
├── Traefik 자체가 안정적으로 유지 → gateway 재시작해도 끊김 없음
└── k3s 기본 내장 → 별도 설치 불필요
```

Traefik과 gateway-service의 역할 구분:

```
Traefik          = 네트워크 레벨. EC2 퍼블릭 IP → 내부 Pod 연결, SSL 처리
gateway-service  = 비즈니스 레벨. JWT 검증, 권한 체크, 헤더 추가, 내부 라우팅
```

---

## 8. 핵심 결정 요약

| 항목 | 결정 | 이유 |
|---|---|---|
| 배포 환경 | k3s on EC2 2대 | IAM 제약, 비용 절감 |
| server 노드 | t3.medium | 컨트롤 플레인 분리 원칙 |
| ES 위치 | t3.medium Pod | t3.large 메모리 확보 |
| Kafka | k3s Pod (KRaft 모드) | Self-healing, 자동 복구, 모니터링 통합 |
| PostgreSQL | k3s Pod | Self-healing, pgvector/pgvector:pg18 |
| 매니페스트 | Helm 단일 차트 + values per service | 9개 쌍둥이 서비스에 최적 |
| 외부 노출 | Traefik (k3s 내장) | 별도 설치 불필요 |
| Secret | kubectl create secret | git 노출 방지 |
