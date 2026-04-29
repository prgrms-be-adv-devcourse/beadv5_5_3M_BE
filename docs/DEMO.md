# 시연용 Schedule Lifecycle 가속 가이드

스케줄 라이프사이클(CART → TICKETING → LOBBY → STREAMING → FINISH)의 시간 오프셋을 환경변수로 줄여서 약 10분 안에 전체 흐름을 시연한다.

**시연 ENV 는 `k8s/values/values-{ticket,creator,streaming}.yaml` 에 직접 박혀있다** — PR 머지 후 CD 가 돌면 자동 적용. 별도 `--set` 명령 불필요.

> 시연 끝난 후 운영값으로 돌리려면 values 파일에서 시연용 라인을 빼고 PR 머지 (또는 EC2 에서 즉시 `--reset-values` — 7번 섹션 참조).

---

## 1. 환경변수 목록 (ISO-8601 Duration)

코드의 `@Value` default 와 각 서비스 `application-prod.yaml` 의 default 가 동일하다. 시연용으로 아래 값으로 override.

| 서비스 | 환경변수 | 운영 default | 시연 적용값 |
|---|---|---|---|
| ticket | `TICKET_CART_CLOSE_LEAD` | `PT24H` | `PT1M` |
| ticket | `TICKET_TICKETING_CLOSE_LEAD` | `PT10M` | `PT2M` |
| creator | `CREATOR_MIN_REGISTRATION_LEAD` | `PT4M` | `PT1M` |
| creator | `CREATOR_MIN_TICKETING_WINDOW` | `PT4M` | `PT2M` |
| creator | `CREATOR_BATCH_WAITING_LEAD` | `PT10M` | `PT1M` |
| creator | `CREATOR_BATCH_COMPLETED_TOLERANCE` | `PT10M` | `PT2M` |
| streaming | `STREAMING_LOBBY_LEAD` | `PT10M` | `PT2M` |
| streaming | `STREAMING_SOON_LEAD` | `PT1M` | `PT30S` |
| streaming | `STREAMING_POST_GRACE` | `PT3M` | `PT2M` |

> **TICKETING_CLOSE_LEAD 와 LOBBY_LEAD 는 같은 시점에 발화하는 jobs 이므로 같은 값으로 맞출 것** (둘 다 PT2M).

---

## 2. 시연용 타임라인

> **중요 제약**: STREAMING phase 길이는 시연용 ENV 로 줄일 수 없다. `endTime = startTime + movie.runningTime` 으로 BE 가 자동 계산하므로, 영상은 항상 실제 재생 시간만큼 흐른다.
>
> **시연 총 소요시간 = 영상 길이 + 약 12분** (CART 3 + IN_PROG 1 + TICKETING 4 + LOBBY 2 + POST_GRACE 2)
>
> 예: 3분 영상 → 총 ~15분, 5분 영상 → 총 ~17분.

스케줄 등록 시 `ticketingTime = 지금+4분`, `startTime = 지금+10분` 으로 잡는다고 가정 (영상 길이 = R).

```
t=0          스케줄 등록
t=0~3분      CART phase            — 시연자가 장바구니 담기 (3분 여유)
t=3분        cartClose             → IN_PROGRESSING, RESERVED 티켓 생성
t=3분~4분    IN_PROGRESSING        — 일괄/부분 예약 처리 (1분)
t=4분        ticketingStart        → TICKETING phase 시작
t=4분~8분    TICKETING phase       — 토스 결제 / 큐 시연 / 환불-drain (4분 충분)
t=8분        ticketingClose       ─┐ 동시 발화
t=8분        reviewAuth           ─┘ → ticket.review.authorized 발행
                                     → streaming-service Entitlement 적재
                                     → 이 시점부터 streaming session 발급 가능
t=8분~10분   LOBBY phase           — 입장 대기 UI 시연 (2분)
t=10분       streamingStart        → STREAMING (HLS 재생 시작)
t=10분~10분+R STREAMING phase      — 영상 실제 길이 R 만큼 재생 (단축 불가)
t=10분+R     streamingFinish       → FINISH
t=10분+R~+2분 POST_GRACE           — 강제퇴장 시연 (2분)
```

> **중요 제약 — ReviewAuthQuartzJob 의존성**: 영상 시청은 `Entitlement` 가 streaming-service DB 에 적재된 후에만 가능하다. Entitlement 는 `ticket.review.authorized` Kafka 메시지를 streaming-service consumer 가 받아서 INSERT 한다. 즉:
>
> 1. `ticketingCloseTime` (= `startTime - TICKETING_CLOSE_LEAD`) 에 ReviewAuthQuartzJob 이 발화
> 2. ticket.review.authorized 발행 → streaming-service 가 ~수 초 안에 Entitlement INSERT
> 3. 그 이후로만 `POST /api/streaming/sessions` 가 sessionToken 발급
>
> **`TICKETING_CLOSE_LEAD` 는 reviewAuth 발화 후 streaming 시작까지의 버퍼.** 너무 짧으면 (예: PT5S) Kafka consume + DB INSERT 못 끝내고 사용자가 session 발급 시도 → entitlement not found → 404. **최소 PT30S 권장, 현재 시연 ENV 인 PT2M 은 안전.**

---

## 3. 사전 준비 — EC2 환경 셋업 (1회만)

EC2 의 `~/k8s` 가 옛 버전이거나 단독 디렉터리인 경우 BE repo 와 동기화한다.

```bash
# BE repo clone (1회만)
git clone https://github.com/prgrms-be-adv-devcourse/beadv5_5_3M_BE.git ~/be-repo

# ~/k8s 갱신 (매번)
cd ~/be-repo && git pull && rsync -av --delete k8s/ ~/k8s/
```

`rsync --delete` 는 BE repo 에서 삭제된 파일도 ~/k8s 에서 정리하여 1:1 일치시킨다.

---

## 4. 시연 직전 — 새 이미지 확인 + 시연 ENV 적용

### 4-1. CI 빌드 완료 확인

```bash
# 최신 이미지 push 시각 확인 (창고 3개 모두)
for svc in ticket-service creator-service streaming-service; do
  echo "=== $svc ==="
  curl -s "https://hub.docker.com/v2/repositories/qor7777777/$svc/tags?page_size=2" \
    | jq '.results[] | {name, last_updated}'
done
```

`latest.last_updated` 가 dev/main 의 머지 시각 이후여야 한다.

### 4-2. Pod 새 이미지로 갱신

`values-*.yaml` 의 `pullPolicy: Always` 덕분에 helm upgrade 한 번이면 새 `:latest` 가 자동 pull 됨. 별도 patch/restart 불필요. (4-3 단계의 helm upgrade 가 이 역할도 함)

이미 같은 시간대에 deploy 한 적 있어 ConfigMap 변경이 없는 경우엔 `rollout restart` 로 강제 재기동:

```bash
for svc in ticket creator streaming gateway; do
  kubectl rollout restart deployment/${svc}-service-microservice -n dev
done

for svc in ticket creator streaming gateway; do
  kubectl rollout status deployment/${svc}-service-microservice -n dev
done
```

### 4-3. 시연 ENV 주입 (PR 머지 후 자동 — 보통 별도 명령 불필요)

값들이 이미 `k8s/values/values-{ticket,creator,streaming}.yaml` 에 박혀있고, dev/main 머지 시점에 CD 의 `k8s-redeploy` 가 자동으로 helm upgrade 한다. ConfigMap 의 시연 ENV 가 Pod 에 주입됨.

머지 없이 EC2 에서 즉시 적용하려면:
```bash
helm upgrade ticket-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-ticket.yaml -n dev --reset-values

helm upgrade creator-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-creator.yaml -n dev --reset-values

helm upgrade streaming-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-streaming.yaml -n dev --reset-values

# rollout 대기
for svc in ticket creator streaming; do
  kubectl rollout status deployment/${svc}-service-microservice -n dev
done
```

> `--reset-values` 는 이전 helm release 의 stale `--set` override 를 정리하는 안전장치.
>
> **단일 노드 K3s 라 maxSurge=0 → 옛 Pod 종료 후 새 Pod 부팅 사이 30~60초 빈 구간이 생긴다.** 그 사이 호출은 모두 5xx 로 떨어지므로 시연 직전이 아닌 **최소 2~3분 전 미리** helm upgrade 하고 rollout status 로 완료 확인할 것.

---

## 5. 검증 (시연 시작 전 필수)

```bash
# 5-1. 새 이미지 SHA 확인 — 옛 이미지 SHA 와 달라야 OK
for svc in ticket creator streaming gateway; do
  echo "=== $svc ==="
  kubectl describe pod -n dev -l app.kubernetes.io/instance=${svc}-service \
    | grep "Image ID:" | head -1
done

# 5-2. ENV 주입 확인
for svc in ticket creator streaming; do
  echo "=== ${svc}-service ==="
  kubectl exec -n dev deployment/${svc}-service-microservice -- \
    env | grep -E "(TICKET|CREATOR|STREAMING)_(CART|TICKETING|MIN|BATCH|LOBBY|SOON|POST)" | sort
done

# 5-3. user-service hostname 정상 (ticket-service)
kubectl exec -n dev deployment/ticket-service-microservice -- env | grep CLIENT_USER
# → http://user-service-microservice.dev.svc.cluster.local:8085

# 5-4. 새 GET 핸들러 살아있는지 (creator-service 의 통합 schedule endpoint)
curl -s -o /dev/null -w "GET /api/creators/schedules → %{http_code}\n" \
  "https://3m-msa.p-e.kr/api/creators/schedules?date=$(date +%Y-%m-%d)"
# → 401 (토큰 없음, 정상) 이면 OK / 405 면 옛 이미지

# 5-5. dry-run schedule 등록 후 Quartz trigger 시각 확인 (가장 확실)
kubectl logs -n dev deployment/ticket-service-microservice -f | grep "Quartz Job 등록"
# 다른 터미널에서 schedule 1개 등록 후 위 로그에서:
# cartCloseTime 이 ticketingTime - 1분 이고
# ticketingCloseTime 이 startTime - 2분 이면 시연 ENV 정상 적용됨
```

---

## 6. 시연 흐름 (시연자 매뉴얼)

1. **schedule 등록** — creator dashboard 에서:
   - 영화 선택
   - 티켓팅 시작 시간 = 지금 + 1분 30초
   - 상영 시작 시간 = 지금 + 3분 30초
   - "일정 편성 완료하기" → 201 OK

2. **schedule 확정** — 캘린더에서 임시 schedule 선택 → "선택 일정 확정" → Kafka 이벤트 → ticket-service 가 Quartz 6 jobs 등록

3. **CART phase (~1분)** — 일반 사용자 계정으로 장바구니 담기 시연. 여러 탭/계정으로 동시 접근하면 큐 시연 가능

4. **TICKETING phase (~2분)** — cartClose 후 자동 진입:
   - 토스 결제 (test 키 기반)
   - 동시 결제 → paying counter → 큐 대기
   - (옵션) 환불 → queue.drain → 대기자 자동 결제 시연

5. **LOBBY → STREAMING** — ticketingClose 시점에 동시 진입. HLS 영상 재생 시작

6. **STREAMING phase (영상 길이만큼)** — 채팅, 동시 시청자 수, 재생 상태 push 시연

7. **FINISH + ForceExit** — endTime 에 STREAMING 종료, 1분 후 강제 퇴장

---

## 7. 시연 직후 — 운영값 복귀

시연 ENV 가 values 파일에 박혀있으므로 **values 파일에서 시연용 라인 제거 → PR → 머지** 가 정석. 머지 시 CD 가 자동으로 helm upgrade 하면서 운영 default 로 폴백.

각 파일에서 제거할 블록:
- `values-ticket.yaml`: `TICKET_CART_CLOSE_LEAD`, `TICKET_TICKETING_CLOSE_LEAD`
- `values-creator.yaml`: `CREATOR_MIN_REGISTRATION_LEAD`, `CREATOR_MIN_TICKETING_WINDOW`, `CREATOR_BATCH_WAITING_LEAD`, `CREATOR_BATCH_COMPLETED_TOLERANCE`
- `values-streaming.yaml`: `STREAMING_LOBBY_LEAD`, `STREAMING_SOON_LEAD`, `STREAMING_POST_GRACE`

> 구체적으로 "# 시연용 lifecycle 가속" 코멘트 + 그 아래 ENV 들을 통째로 삭제하면 됨.

머지 없이 EC2 에서 즉시 운영 모드 복귀가 필요하면:
```bash
# 1) values 파일에서 시연용 라인 직접 제거
sed -i '/# 시연용 lifecycle 가속/,/^$/d' \
  ~/k8s/values/values-ticket.yaml \
  ~/k8s/values/values-creator.yaml \
  ~/k8s/values/values-streaming.yaml

# 2) 운영값으로 helm upgrade (--reset-values 로 stale --set 도 같이 정리)
helm upgrade ticket-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-ticket.yaml -n dev --reset-values
helm upgrade creator-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-creator.yaml -n dev --reset-values
helm upgrade streaming-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-streaming.yaml -n dev --reset-values
```

검증:
```bash
kubectl exec -n dev deployment/ticket-service-microservice -- env | grep -E "TICKET_(CART|TICKETING)" | sort
# 출력 없거나 운영 default 값(PT24H, PT10M)이면 OK
```

---

## 8. 트러블슈팅 (실제 시연 준비 중 만난 이슈들)

### 8-1. `405 Method Not Allowed` on `GET /api/creators/schedules?date=...`
배포된 이미지가 PR #312 (`fix/main → dev/main`) 머지 이전 버전. 새 GET 핸들러 (`dc12d4d`) 가 없어서 405. 해결: 4-2 단계 (강제 pull) 진행.

### 8-2. `400 Bad Request` on `POST /api/creators/schedules`
옛 이미지의 hardcoded 4분 lead 검증에 걸림. FE 에서 1~2분 lead 로 등록 시도 시 발생. 해결: 4-2 단계 진행 후 `@Value Duration` 코드 + 시연 ENV 동시 적용.

### 8-3. `UnknownHostException: user-service.dev.svc.cluster.local`
ConfigMap 의 `CLIENT_USER_BASE_URL` 등 hostname 이 `*-microservice` 빠진 옛 값. EC2 의 `~/k8s` 가 갱신 안 된 정적 디렉터리인 경우. 해결: 3 단계 (EC2 환경 셋업) 진행.

### 8-4. `500` on `/files/posters/...` 폭발
helm upgrade 직후 ~30~60초 동안 발생하는 정상 현상. maxSurge=0 라 옛 Pod 종료 → 새 Pod 부팅 사이 빈 구간. rollout status 가 끝나면 자연 해소.

### 8-5. `409 Conflict` on `POST /api/tickets/{id}/pay`
`alreadyPaid` (이미 CONFIRMED) 또는 재고/paying 카운터 한도 초과. ticket-service 로그에서 정확한 exception 확인:
```bash
kubectl logs -n dev deployment/ticket-service-microservice --tail=200 | grep -B 2 -A 15 "/pay\|Exception"
```

### 8-6. `404` on `POST /api/streaming/sessions`
streaming-service 이미지가 옛 버전이거나 schedule 의 video location 미설정. 4-2 단계로 streaming-service 도 같이 갱신했는지 확인.

### 8-7. `helm upgrade --set` 만 했더니 다른 ENV 가 사라짐
이번 사례에서는 발생 안 했지만, chart 의 ConfigMap template 가 `toYaml .Values.env` 식이면 `--set` 이 list/map 을 통째 덮어쓸 수 있음. **시연 ENV + 기존 critical ENV (`CLIENT_USER_BASE_URL` 등) 둘 다 명시적으로 `--set`** 하면 안전:
```bash
helm upgrade ticket-service ./k8s/charts/microservice \
  -f ~/k8s/values/values-ticket.yaml -n dev \
  --set env.CLIENT_USER_BASE_URL=http://user-service-microservice.dev.svc.cluster.local:8085 \
  --set env.TICKET_CART_CLOSE_LEAD=PT1M \
  --set env.TICKET_TICKETING_CLOSE_LEAD=PT2M
```

---

## 9. 변경 파일 (참고)

| 파일 | 변경 |
|---|---|
| `ticket-service/.../infrastructure/event/ScheduleEventListener.java` | hardcoded `minusHours(24)` / `minusMinutes(10)` → `@Value Duration` |
| `streaming-service/.../infrastructure/scheduler/QuartzSchedulerAdapter.java` | static `LOBBY_LEAD`/`SOON_LEAD`/`POST_GRACE` → 인스턴스 `@Value Duration` |
| `creator-service/.../application/service/ScheduleManageService.java` | 등록/확정 검증의 `plusMinutes(4)` 두 곳 → `@Value Duration` |
| `creator-service/.../application/service/ScheduleBatchService.java` | `plusMinutes(10)` / `minusMinutes(10)` → `@Value Duration` |
| `ticket-service/src/main/resources/application-prod.yaml` | `ticket.lifecycle.*` 섹션 추가 |
| `creator-service/src/main/resources/application-prod.yaml` | `creator.schedule.*` 섹션 추가 |
| `streaming-service/src/main/resources/application-prod.yaml` | `streaming.lifecycle.*` 섹션 추가 |
| `cinestream_fe/src/app/pages/CreatorDashboard.tsx` | "(상영 2일 이상 전)" 문구 제거 (BE 가 ENV 로 가변이라 부정확한 안내) |

## 10. 변경하지 않는 것

- `ticket-service/.../DailyTicketFeeProvideScheduler` (1:00 AM cron) — 시연 범위 밖
- 모든 상태 머신, Kafka 토픽 이름, Redis 키 prefix, 비즈니스 정책 (동시 결제 락, 부분 예약 규칙 등)
- `CookieCompensationHelper`, `SelfPaymentService`, `QueueDrainConsumer` 등 핵심 use-case 로직
- creator-service 의 ScheduleResponse / ScheduleController 의 endpoint 시그니처