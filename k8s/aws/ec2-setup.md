# EC2 + K3s 셋업 절차 (수동, 1회)

본 문서는 AWS 콘솔에서 직접 클릭으로 진행하는 1회성 인프라 셋업 절차입니다.
완료 후 본 문서는 reference 용으로 남겨두고, 운영 명령은 [k8s/README.md](../README.md) 참조.

**전제 조건**
- AWS 콘솔 로그인 가능 (운영진에서 받은 IAM 계정)
- 받은 자원: EC2 1대 (t3.large), EBS 50GB, EIP 1개, VPC 1개, S3 1개
- 리전: `ap-northeast-2` (서울)

---

## 1. VPC + Subnet + Internet Gateway

> AWS 콘솔 → 검색 `VPC` → `Your VPCs` → `Create VPC`

| 항목 | 값 |
|---|---|
| Resources to create | **VPC and more** (Subnet/IGW/Route 자동 생성) |
| Name tag auto-generation | `beadv5-k3s` |
| IPv4 CIDR | `10.0.0.0/16` |
| AZ 수 | 1 |
| Public subnet 수 | 1 |
| Private subnet 수 | 0 |
| NAT gateways | None |
| VPC endpoints | None |

생성 결과:
- VPC: `beadv5-k3s-vpc`
- Subnet: `beadv5-k3s-subnet-public1-ap-northeast-2a`
- Internet Gateway: 자동 attach

---

## 2. Security Group

> VPC 콘솔 → `Security Groups` → `Create security group`

| 항목 | 값 |
|---|---|
| Name | `beadv5-k3s-sg` |
| Description | `K3s single node + SSH + HTTP/HTTPS + K3s API` |
| VPC | `beadv5-k3s-vpc` |

**Inbound rules (4개)**

| Type | Protocol | Port | Source | 설명 |
|---|---|---|---|---|
| SSH | TCP | 22 | My IP | 본인 PC SSH 배포 (조원도 작업하면 그 IP 도 추가) |
| HTTP | TCP | 80 | 0.0.0.0/0 | 시연 트래픽 |
| HTTPS | TCP | 443 | 0.0.0.0/0 | (도메인 후 사용) |
| Custom TCP | TCP | 6443 | My IP | K3s API (kubectl 로컬 접속) |

**Outbound rules**: 기본값 (`All traffic, 0.0.0.0/0`) 유지

> ⚠️ Outbound 의 `52.78.88.98:9092` (외부 Kafka) 는 0.0.0.0/0 안에 포함되어 자동 허용.

---

## 3. EC2 인스턴스 생성

> EC2 콘솔 → `Instances` → `Launch instances`

| 항목 | 값 |
|---|---|
| Name | `beadv5-k3s-node` |
| AMI | **Ubuntu Server 22.04 LTS** (HVM, x86_64) |
| Instance type | **t3.large** |
| Key pair | `Create new` → 이름 `beadv5-k3s-key`, 타입 RSA, 형식 .pem → 다운로드 |
| Network → VPC | `beadv5-k3s-vpc` |
| Network → Subnet | `beadv5-k3s-subnet-public1-ap-northeast-2a` |
| Network → Auto-assign public IP | **Disable** (EIP 따로) |
| Network → Security group | `Select existing` → `beadv5-k3s-sg` |
| Storage | **50 GB**, gp3 |

**중요**:
- PEM 파일 다운로드 직후 안전한 곳으로 이동 (`~/.ssh/beadv5-k3s-key.pem`)
- chmod 600 권한 (Windows: `icacls` 로 동등 처리)

---

## 4. Elastic IP

> EC2 콘솔 → `Elastic IPs` → `Allocate Elastic IP address`

| 항목 | 값 |
|---|---|
| Network Border Group | `ap-northeast-2` |
| Public IPv4 address pool | `Amazon's pool` |

**Associate**: 생성된 EIP → Actions → `Associate Elastic IP address` → Instance: `beadv5-k3s-node`

→ 발급된 IPv4 주소 메모 (예: `13.125.xxx.xxx`).

---

## 5. SSH 접속 검증

```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@<EIP>
```

처음 fingerprint 확인 → `yes`. 접속되면 OK.

---

## 6. K3s 설치 (EC2 안)

```bash
# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# swap 8GB 추가 (OOM 보험)
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# K3s 설치 (버전 고정)
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.30.3+k3s1 sh -

# 노드 검증
sudo kubectl get nodes
```

---

## 7. kubeconfig 본인 PC 로 복사

본인 PC 에서:
```bash
ssh -i ~/.ssh/beadv5-k3s-key.pem ubuntu@<EIP> 'sudo cat /etc/rancher/k3s/k3s.yaml' > ~/.kube/config-beadv5-k3s
sed -i 's|https://127.0.0.1:6443|https://<EIP>:6443|' ~/.kube/config-beadv5-k3s
export KUBECONFIG=~/.kube/config-beadv5-k3s
kubectl get nodes
```

---

## 8. S3 버킷 + IAM User (Task 1.6 참조)

별도 절차. [iam-user-policy.json](iam-user-policy.json) 정책으로 IAM User 생성 후 Access Key 발급.

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| SSH `Permission denied (publickey)` | PEM 권한 / 키 페어 mismatch | `chmod 600`, AWS 콘솔에서 EC2 → Security → Key pair 확인 |
| SSH `Connection timed out` | 보안그룹 22번 미허용 / IP 변경 | SG Inbound 22 의 Source 갱신 (My IP 새로고침) |
| `kubectl get nodes` connection refused | 6443 보안그룹 미허용 | SG Inbound 6443 추가 |
| K3s 설치 후 노드 NotReady | CNI 초기화 1~2분 대기 필요 | `sudo kubectl get pod -A` 로 모든 Pod Running 확인 |
