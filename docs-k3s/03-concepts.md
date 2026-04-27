# 개념 용어 사전

> 모르는 개념이 나오면 여기서 찾아볼 것

---

## 계층 구조

```
k3s (프로그램)
  │
  │ server 모드 실행      agent 모드 실행
  │       │                     │
  │   EC2 t3.medium         EC2 t3.large
  │   (server 노드)         (agent 노드)
  │       │                     │
  │       └──────────┬──────────┘
  │               묶여서 탄생
  │                   │
  └──────────→  클러스터 (하나의 조직)
                      │
                ┌─────┴──────┐
           namespace       namespace
           kube-system        dev         ← 우리가 쓰는 공간
                               │
                     ┌─────────┼─────────┐
                    Pod       Pod        Pod
               movie-service user-service elasticsearch
```

### 계층 요약

```
노드 (서버 컴퓨터)
  └── k3s 실행
        └── 클러스터 탄생
              └── namespace (논리적 격리 공간)
                    └── Pod (컨테이너 감싸는 최소 단위)
```

---

## k3s vs k3d vs k8s

```
k8s (Kubernetes)  = 원본. 대규모 환경용. 컨트롤 플레인 메모리 1.3GB
k3s               = 경량화 버전. EC2 같은 소규모에 설치. 컨트롤 플레인 0.8GB
k3d               = 로컬에서 k3s를 Docker 컨테이너로 흉내내는 도구

로컬 k3d에서 검증한 YAML → EC2 k3s에 그대로 사용 가능 (100% 호환)
```

### 비유

```
EC2에 k3s  =  실제 주방에서 요리 (실수하면 손님한테 나감)
k3d        =  집에서 레시피 연습 (실수해도 나만 먹음)
             근데 레시피(YAML)는 완전히 동일
```

### 구조 비교

```
EC2 k3s                              로컬 k3d
────────────────────────             ────────────────────────────
EC2 t3.medium (진짜 서버)            Docker 컨테이너 k3d-server-0
EC2 t3.large (진짜 서버)             Docker 컨테이너 k3d-agent-0
클러스터                             클러스터
└── namespace: dev                   └── namespace: dev
      └── Pod: movie-service               └── Pod: movie-service
```

### k3d 로컬 실제 구조 (Docker Desktop 기준)

```
[로컬 맥북 — Docker Desktop]
│
├── Docker 컨테이너: k3d-local-cluster-server-0  (t3.medium 역할)
│     └── k3s server 프로세스 (컨트롤 플레인 + 워커)
│
├── Docker 컨테이너: k3d-local-cluster-agent-0   (t3.large 역할)
│     └── k3s agent 프로세스 (워커만)
│
├── Docker 컨테이너: k3d-local-cluster-serverlb  ← 로드밸런서 (K8s 노드 아님)
└── Docker 컨테이너: k3d-local-cluster-tools     ← k3d 내부 도구 (K8s 노드 아님)
│
│  ↓ server + agent가 묶여서 탄생 ↓
│
└── 클러스터 (local-cluster)
      │
      ├── namespace: kube-system   (K8s 시스템, 건드리지 마)
      │     ├── Pod: coredns          → node: server-0
      │     ├── Pod: traefik          → node: server-0
      │     └── Pod: metrics-server   → node: server-0
      │
      └── namespace: dev
            └── Pod: nginx-test       → node: agent-0  ← 컨트롤 플레인이 배치 결정
```

> Docker Desktop에서 컨테이너가 4개 보이지만 K8s 노드는 2개인 이유:
serverlb, tools는 k3d 운영용 보조 컨테이너일 뿐, K8s 클러스터 노드가 아님.

|  | EC2 k3s | k3d 로컬 |
| --- | --- | --- |
| 노드 | EC2 인스턴스 (진짜) | Docker 컨테이너 (가짜) |
| kubectl 명령어 | 동일 | 동일 |
| YAML 파일 | 동일 | 동일 |
| 실제 서버 필요? | ✅ | ❌ |
| 비용 | EC2 비용 발생 | 무료 |
| 실수하면? | 운영 서버 영향 | Docker 재시작으로 해결 |
| 용도 | 실제 배포 | 연습 / 테스트 |

### 흐름

```
로컬 k3d에서 YAML 완성
        ↓
EC2 k3s에 그대로 올림
(환경별로 달라지는 값만 수정: IP, 도메인 등)
```

---

## 클러스터 (Cluster)

노드들을 묶어서 하나의 조직으로 관리하는 단위.
k3s 설치 후 server + agent가 연결되면 클러스터가 탄생함.

```
k3s = 도구
클러스터 = 그 도구가 만들어낸 결과물
(Java = 도구, JVM = 실행 환경 — 같은 관계)
```

---

## server 노드 vs agent 노드

```
server 노드 (t3.medium)
├── 컨트롤 플레인: 클러스터 관리 (배치 결정, 상태 저장)
└── 워커: Pod도 실행 가능 (k3s는 겸직 허용)

agent 노드 (t3.large)
└── 워커: Pod 실행만 함
```

kubectl 명령은 server에서만 동작. agent에는 kubeconfig 없음.

> **왜 server가 Pod 실행을 겸하냐?**
원칙적으로는 컨트롤 플레인 전용 노드는 Pod를 안 띄움.
k3s는 경량화 목적이라 서버 2대짜리 환경에서 server 노드를 관리 전용으로 놀리면 낭비.
단, 겸직의 위험: movie-service가 메모리 폭발 → 컨트롤 플레인도 메모리 부족 → 클러스터 전체 불안정.
이게 Resource Contention(리소스 경합). 실무 대규모 환경에서는 taint로 컨트롤 플레인 노드에 Pod 배치를 강제 차단.

> **왜 t3.medium이 server냐?**
컨트롤 플레인과 워크로드를 분리하는 게 원칙에 맞음.
medium은 두뇌 역할(컨트롤 플레인) + ES만 담당, large는 실제 서비스 실행.
"크니까 agent"가 아니라 "ES 격리 필요에서 역할이 결정된 것"

### 클러스터는 통합 관리다 — 각 노드가 알아서 하는 게 아님

흔한 오해: "t3.large는 자기 서비스 관리, t3.medium은 자기 서비스 관리"
실제: **컨트롤 플레인(t3.medium 안)이 모든 결정을 내림. 노드는 지시를 받아서 실행만.**

```
❌ 틀린 이해
├── t3.medium →  자기 서비스 알아서 관리
└── t3.large  →  자기 서비스 알아서 관리

✅ 맞는 이해
컨트롤 플레인 (t3.medium 안에 있는 뇌)
├── "movie-service 어디 띄울지?" → t3.large 여유 있음 → 거기 배치
└── "ES 어디 띄울지?" → t3.medium taint 설정 보임 → 거기 배치
      ↓
t3.medium, t3.large는 지시받은 대로 실행만 함
```

비유:

```
컨트롤 플레인  =  본사 (모든 결정)
t3.medium      =  1공장 (본사 지시대로 실행 + 본사 건물도 여기 있음)
t3.large       =  2공장 (본사 지시대로 실행)
```

공장은 본사 지시 없이 스스로 결정하지 않는다.

---

## 컨트롤 플레인

클러스터의 두뇌. server 노드 안에서 실행됨.

```
API 서버   → kubectl 명령 받아서 처리
스케줄러   → "Pod를 어느 노드에 띄울지" 결정
etcd       → 클러스터 전체 상태 저장 (Key-Value DB)
```

---

## 스케줄러가 Pod 배치 결정하는 순서

```
1. Taint/Toleration  → "이 노드 들어갈 자격 있어?"
2. Node Selector     → "특정 노드 지정했어?"
3. 여유 자원         → "메모리/CPU 충분해?"
```

---

## Taint / Toleration

```
Taint      = 노드에 거는 자물쇠 ("아무나 못 들어와")
Toleration = Pod가 가진 열쇠 ("나는 들어갈 수 있어")
nodeSelector = 특정 노드 지정 (Toleration만으론 부족할 때 함께 사용)
```

우리 설정:
```
medium Taint: dedicated=es:NoSchedule
ES Pod Toleration: dedicated=es + nodeSelector: ip-172-31-42-216
→ ES만 medium에 뜸, 나머지는 large에 뜸
```

---

## Namespace

클러스터 안의 논리적 격리 공간. 물리적 노드와 무관.

```
언제 나누냐?
├── 환경 분리: dev / staging / prod  ← 가장 흔한 패턴
├── 팀 분리:   team-a / team-b
└── 목적 분리: app / monitoring / infra

서비스별로 나누는 건 실무에서도 잘 안 함.
→ NetworkPolicy 없으면 어차피 통신 다 됨
→ 조개 프로젝트는 dev 하나면 충분
```

> **⚠️ 중요: namespace는 node를 구분하는 게 아니다**
>
> ```
> namespace = 논리적 구분 (이름표)
> node      = 물리적 구분 (실제 서버)
> ```
>
> 같은 namespace의 Pod들이 서로 다른 node에 흩어져 있을 수 있음.
> 어느 node에 뜰지는 namespace가 결정하는 게 아니라 컨트롤 플레인 스케줄러가 결정.
>
> ```
> ❌ 틀린 이해
> namespace: dev → 무조건 t3.large에만 뜸
>
> ✅ 맞는 이해
> namespace: dev
>   ├── Pod: movie-service  → 컨트롤 플레인이 node: server-0 배치 결정
>   └── Pod: elasticsearch  → 컨트롤 플레인이 node: agent-0 배치 결정
>
> 같은 namespace, 다른 node에 뜰 수 있음
> ```

K8s 기본 네임스페이스:

```
default         ← 네임스페이스 안 지정하면 여기 들어감
kube-system     ← K8s 시스템 Pod들 (coredns, traefik...) — 건드리지 마
kube-public     ← 클러스터 외부에 공개할 정보 (거의 안 씀)
kube-node-lease ← 노드 생존 여부 체크용 — 건드리지 마
```

우리는 dev 하나만 사용.

---

## Pod

컨테이너를 감싸는 K8s 최소 단위. Pod가 죽으면 K8s가 자동으로 새 Pod 생성.
이때 IP가 바뀌므로 Service로 고정 DNS 주소를 사용함.

---

## KRaft 모드 (Kafka)

Kafka 2.8+에서 도입. ZooKeeper 없이 Kafka 단독으로 실행 가능한 모드.

```
기존 구조 (ZooKeeper 필요)
ZooKeeper (메타데이터 저장) + Kafka 브로커
→ StatefulSet 2개, PVC 2개, 관리 포인트 2배

KRaft 구조 (ZooKeeper 불필요)
Kafka 브로커 (메타데이터 + 메시지 처리 자체 담당)
→ StatefulSet 1개, PVC 1개
```

Kafka 3.3부터 production ready 공식 인정. 우리 프로젝트는 KRaft 모드 사용.

---

## Deployment vs StatefulSet

```
Deployment  → 무상태 앱 (Spring Boot 서비스 등)
              Pod 죽으면 새 Pod로 교체, 데이터 없음

StatefulSet → 상태가 있는 앱 (PostgreSQL, ES 등)
              Pod 이름 고정 (postgresql-0), PersistentVolume으로 데이터 유지
```

---

## Service (K8s)

Pod에 접근하는 고정 DNS 주소 제공.

```
Pod IP: 10.0.0.5 → 재시작 시 10.0.0.9로 바뀜
Service: movie-service.dev.svc.cluster.local → 항상 살아있는 Pod로 연결
```

> **CoreDNS**: `kube-system` 네임스페이스에 있는 Pod.
`movie-service.dev.svc.cluster.local` 같은 주소를 해석해줌.
Pod → CoreDNS 조회 → 실제 Pod IP 반환 → 통신.

---

## Traefik

k3s에 기본 내장된 Ingress Controller. 외부 인터넷 → 클러스터 내부 Pod 연결을 담당.

```
역할 구분
Traefik          = 네트워크 레벨. "이 요청 어느 Pod로 보낼지"
gateway-service  = 비즈니스 레벨. "보내도 되는지 판단 (인증/권한) + 헤더 추가"
```

### 왜 gateway-service로 바로 안 가나

Pod는 클러스터 내부 IP(10.42.x.x)만 가짐 → 외부에서 직접 접근 불가.
누군가 EC2 퍼블릭 IP → 내부 Pod 연결을 해줘야 함. 그게 Traefik.

```
사용자
    ↓ HTTPS (443)
Traefik  ← EC2 퍼블릭 IP에 바인딩, SSL 처리
    ↓
gateway-service  ← JWT 검증, 권한 체크, 헤더 추가
    ↓
각 내부 서비스
```

### Traefik이 없으면 (NodePort 방식과 비교)

```
NodePort 방식 (Traefik 없이 gateway 직접 노출)
├── 포트 번호 30000~32767 범위 강제 → 80/443 못 씀
├── http://ec2-ip:30080 처럼 포트 번호를 URL에 써야 함
├── SSL 인증서 처리를 gateway가 직접 해야 함
└── gateway Pod 재시작 시 순간 외부 접근 끊김

Traefik 방식
├── 80/443 표준 포트 사용 → 사용자가 포트 번호 안 써도 됨
├── SSL 인증서 자동 갱신 → gateway가 신경 안 써도 됨
├── Traefik 자체가 안정적으로 유지 → gateway 재시작해도 Traefik은 살아있음
└── k3s 기본 내장 → 별도 설치 불필요
```

---

## Helm

YAML 템플릿 엔진 + 배포 관리 도구.

```
붕어빵 틀    = charts/microservice/templates/
팥/슈크림    = values/values-ai.yaml
붕어빵       = 실제 배포되는 K8s 리소스

helm upgrade --install ai-service ./charts/microservice \
  -f values/values-ai.yaml \
  --set image.tag=abc123
```

배포 이력을 클러스터 안에 Secret으로 저장함 (`sh.helm.release.v1.*`).

---

## ConfigMap vs Secret

```
ConfigMap → 평문 환경변수 (DB_NAME, KAFKA_GROUP_ID 등)
            git에 올려도 됨, kubectl get configmap으로 누구나 볼 수 있음

Secret    → 민감 정보 (비밀번호, API 키 등)
            base64 인코딩 저장, RBAC으로 접근 제어
            절대 git에 올리지 말 것
```

---

## DDL_AUTO

Hibernate(JPA)가 시작 시 DB 스키마를 어떻게 처리할지 결정.

```
create   → 테이블 새로 만듦 (기존 데이터 삭제)
update   → 변경사항만 업데이트
validate → 테이블이 엔티티와 일치하는지 확인만 (아무것도 안 만듦)
none     → 아무것도 안 함
```

우리는 `validate` 사용. DB가 이미 세팅되어 있어야 앱이 뜸.

---

## Reconciliation Loop

K8s의 핵심 동작 원리.

```
"desired state(원하는 상태)" 선언
    ↓
control loop가 현재 상태와 계속 비교
    ↓
다르면 자동으로 맞춤

예: replicas: 2 선언 → Pod 1개 죽음 → 즉시 1개 더 띄움
```

---

## QoS Class

메모리 부족 시 K8s가 어떤 Pod를 먼저 종료할지 결정하는 기준.

```
Guaranteed (절대 안 죽음) → requests == limits
Burstable (여유 있을 때 더 씀) → requests < limits
BestEffort (가장 먼저 죽음) → requests/limits 미설정
```

---

## 여러 클러스터를 두려면?

```
클러스터 A (개발용)              클러스터 B (운영용)
├── EC2-1: k3s server           ├── EC2-3: k3s server
└── EC2-2: k3s agent            └── EC2-4: k3s agent

→ 클러스터끼리는 완전히 독립. 서로 모름.
```

접근 시 context로 구분:

```bash
kubectl config use-context cluster-A
kubectl config use-context cluster-B
```

---

## CS 용어 모음

| 용어 | 뜻 | 언제 나오나 |
|---|---|---|
| Reconciliation Loop | 원하는 상태와 현재 상태를 계속 비교해서 맞추는 루프 | K8s Self-healing 설명할 때 |
| Desired State | 내가 선언한 목표 상태 (replicas: 2) | Deployment 설명할 때 |
| SPOF | Single Point of Failure. 이게 죽으면 전체가 죽는 지점 | server 노드 1개일 때의 위험 |
| Resource Contention | 같은 자원(CPU/메모리)을 두고 프로세스들이 경쟁 | 컨트롤 플레인 + 앱 Pod 같은 노드에 있을 때 |
| Rolling Update | Pod를 하나씩 교체해서 무중단 배포 | 배포 전략 설명할 때 |
| HPA | Horizontal Pod Autoscaler. CPU/메모리 기준 Pod 수 자동 조절 | 스케일링 설명할 때 |
| FQDN | Fully Qualified Domain Name. movie-service.dev.svc.cluster.local | Service DNS 설명할 때 |
| etcd | 클러스터 상태를 저장하는 분산 Key-Value 저장소 | server 노드 내부 구조 |
| QoS Class | K8s가 메모리 부족 시 어느 Pod를 먼저 죽일지 결정하는 기준 | limits/requests 설정할 때 |
| Idempotent | 같은 명령을 여러 번 실행해도 결과가 같음 (helm upgrade --install) | Helm 배포 설명할 때 |
| initContainer | 본 컨테이너 시작 전 실행되는 초기화 컨테이너 (nori 설치 등) | ES 플러그인 설치 |
| PVC | PersistentVolumeClaim. Pod가 재시작해도 데이터 유지되는 디스크 요청 | StatefulSet 설명할 때 |
