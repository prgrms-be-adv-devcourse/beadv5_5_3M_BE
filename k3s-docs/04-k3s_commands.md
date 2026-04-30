# K3s 명령어 정리

> VSCode + SSH 환경 기준 (master 노드: `ubuntu@ip-172-31-42-216`)

---

## 초기 설정

### kubeconfig 권한 수정 (kubectl 사용 시 permission denied 발생할 때)

```bash
sudo chmod 644 /etc/rancher/k3s/k3s.yaml
```

> k3s 서비스 재시작 시 권한이 600으로 리셋될 수 있으므로 재발 시 다시 실행

### alias 등록

```bash
echo "alias k='kubectl'" >> ~/.bashrc && source ~/.bashrc
```

이후 `kubectl` 대신 `k`로 단축 사용 가능

---

## Pod 조회

```bash
# dev 네임스페이스 전체 pod 목록
kubectl get pod -n dev

# 상태 실시간 갱신 (watch)
kubectl get pod -n dev -w

# 노드/IP 등 상세 정보 포함
kubectl get pod -n dev -o wide

# 특정 서비스만 필터
kubectl get pod -n dev | grep gateway
```

---

## Pod 상태 상세 확인

```bash
# pod 상세 정보 (이벤트, probe 실패 원인 등)
kubectl describe pod -n dev <pod-name>

# StatefulSet 목록
kubectl get statefulset -n dev

# Deployment 목록
kubectl get deployment -n dev
```

---

## 로그 확인

```bash
# 기본 로그
kubectl logs -n dev <pod-name>

# 실시간 스트리밍
kubectl logs -n dev <pod-name> -f

# 마지막 100줄만
kubectl logs -n dev <pod-name> --tail=100

# 실시간 + 마지막 50줄부터
kubectl logs -n dev <pod-name> -f --tail=50

# 재시작 후 죽기 전 로그 확인 (원인 파악 시 유용)
kubectl logs -n dev <pod-name> --previous
```

---

## Pod 재시작 / 삭제

```bash
# pod 삭제 (StatefulSet/Deployment면 자동 재생성됨)
kubectl delete pod -n dev <pod-name>

# Deployment 정상 재시작
kubectl rollout restart deployment -n dev <deployment-name>

# dev 네임스페이스 전체 Deployment 재시작
kubectl rollout restart deployment -n dev
```

---

## 리소스 사용량

```bash
# pod별 CPU/메모리
kubectl top pod -n dev

# 노드별 CPU/메모리
kubectl top node
```

---

## 디스크 / 이미지 관리

```bash
# 디스크 사용량 확인
df -h

# 노드에 캐시된 컨테이너 이미지 목록 (워커 노드에서 실행)
sudo crictl images

# 사용하지 않는 이미지 일괄 삭제 (dangling 이미지 정리)
sudo crictl rmi --prune
```

---

## Helm (서비스 배포)

```bash
# 현재 배포된 release 목록
helm list -n dev

# 특정 서비스 업그레이드 (예: gateway)
helm upgrade gateway k8s/charts/microservice -n dev -f k8s/values/values-gateway.yaml

# 전체 서비스 업그레이드
helm upgrade --install gateway-service k8s/charts/microservice/ -f k8s/values/values-gateway.yaml -n dev                                                                                            
helm upgrade --install creator-service k8s/charts/microservice/ -f k8s/values/values-creator.yaml -n dev                                                                                            
helm upgrade --install payment-service k8s/charts/microservice/ -f k8s/values/values-payment.yaml -n dev                                                                                            
helm upgrade --install settlement-service k8s/charts/microservice/ -f k8s/values/values-settlement.yaml -n dev                                                          
helm upgrade --install ticket-service k8s/charts/microservice/ -f k8s/values/values-ticket.yaml -n dev                                                                                              
helm upgrade --install user-service k8s/charts/microservice/ -f k8s/values/values-user.yaml -n dev
helm upgrade --install movie-service k8s/charts/microservice/ -f k8s/values/values-movie.yaml -n dev                                                                                               
helm upgrade --install streaming-service k8s/charts/microservice/ -f k8s/values/values-streaming.yaml -n dev                                                           
helm upgrade --install ai-service k8s/charts/microservice/ -f k8s/values/values-ai.yaml -n dev 
  
# 전체 서비스 한번에 업그레이드
for svc in gateway creator payment settlement ticket user movie streaming ai; do
  helm upgrade $svc k8s/charts/microservice -n dev -f k8s/values/values-$svc.yaml
done
```

---

## Pod 이름 변수로 저장 (스크립트 활용)

```bash
# 라벨 셀렉터로 pod 이름 가져오기 (이름이 바뀌어도 동일하게 사용 가능)
POD=$(kubectl get pod -n dev -l app=gateway -o jsonpath='{.items[0].metadata.name}')
kubectl logs -n dev $POD -f
```

---

## 디버깅 순서

```
1. kubectl get pod -n dev                        # 전체 상태 확인
2. kubectl describe pod -n dev <문제-pod>         # 이벤트/probe 실패 원인
3. kubectl logs -n dev <pod> --previous          # 죽기 전 로그
4. kubectl logs -n dev <pod> -f                  # 현재 로그 실시간
```

---

## 서비스 포트 참고

| 서비스      | 포트 |
|------------|------|
| gateway    | 8000 |
| creator    | 8080 |
| payment    | 8081 |
| settlement | 8083 |
| ticket     | 8084 |
| user       | 8085 |
| movie      | 8086 |
| streaming  | 8088 |
| ai         | 8089 |
