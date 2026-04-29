# 시연용 Schedule Lifecycle 가속 가이드

스케줄 라이프사이클(CART → TICKETING → LOBBY → STREAMING → FINISH)의 시간 오프셋을 환경변수로 줄여서 약 10분 안에 전체 흐름을 시연한다. 코드/yaml 의 default 는 운영값 그대로이고, ENV 미주입 시 자동 폴백된다.

## 1. 환경변수 목록 (ISO-8601 Duration)

코드의 `@Value` default 와 각 서비스 `application-prod.yaml` 의 default 가 동일하다. 시연용으로 아래 값으로 override.

| 서비스 | 환경변수 | 운영 default | 시연 권장값 |
|---|---|---|---|
| ticket | `TICKET_CART_CLOSE_LEAD` | `PT24H` | `PT1M` |
| ticket | `TICKET_TICKETING_CLOSE_LEAD` | `PT10M` | `PT30S` |
| creator | `CREATOR_MIN_REGISTRATION_LEAD` | `PT4M` | `PT1M` |
| creator | `CREATOR_MIN_TICKETING_WINDOW` | `PT4M` | `PT1M` |
| creator | `CREATOR_BATCH_WAITING_LEAD` | `PT10M` | `PT30S` |
| creator | `CREATOR_BATCH_COMPLETED_TOLERANCE` | `PT10M` | `PT1M` |
| streaming | `STREAMING_LOBBY_LEAD` | `PT10M` | `PT30S` |
| streaming | `STREAMING_SOON_LEAD` | `PT1M` | `PT10S` |
| streaming | `STREAMING_POST_GRACE` | `PT3M` | `PT1M` |

## 2. 시연용 타임라인

> **중요 제약**: STREAMING phase 길이는 시연용 ENV 로 줄일 수 없다. `endTime = startTime + movie.runningTime` 으로 BE 가 자동 계산하므로, 영상은 항상 실제 재생 시간만큼 흐른다. 시연 총 소요시간 = **약 3분 30초 (앞쪽 phase 합계) + 영상 길이 + 1분 (POST_GRACE)** ≈ **영상 길이 + 4분 30초**.
>
> 예: 5분짜리 영상 → 총 ~9분 30초, 7분짜리 → 총 ~11분 30초. 10분 시연 목표라면 5~6분 영상 권장.

스케줄 등록 시 `ticketingTime = 지금+1분`, `startTime = 지금+3분` 으로 잡는다고 가정 (영상 길이 = R).

```
t=0           스케줄 등록
t=0~1분       CART phase            — 시연자가 장바구니 담기
t=1분         cartClose             → IN_PROGRESSING, RESERVED 티켓 생성
t=1분         ticketingStart        → TICKETING phase 시작
t=1분~2분30초  TICKETING phase      — 토스 결제 / 큐 시연 / 환불-drain
t=2분30초     ticketingClose        → LOBBY (queue.terminated 발행)
t=2분30초~3분  LOBBY phase          — 입장 대기 UI (30초)
t=3분         streamingStart        → STREAMING (HLS 재생 시작)
t=3분~3분+R   STREAMING phase       — 영상 실제 길이 R 만큼 재생 (단축 불가)
t=3분+R       streamingFinish       → FINISH
t=3분+R~4분+R POST_GRACE            — 강제퇴장 시연 (1분)
```

## 3. 시연 직전 — 적용

```bash
helm upgrade ticket-service ./k8s/charts/microservice \
  -f k8s/values/values-ticket.yaml -n dev \
  --set env.TICKET_CART_CLOSE_LEAD=PT1M \
  --set env.TICKET_TICKETING_CLOSE_LEAD=PT30S

helm upgrade creator-service ./k8s/charts/microservice \
  -f k8s/values/values-creator.yaml -n dev \
  --set env.CREATOR_MIN_REGISTRATION_LEAD=PT1M \
  --set env.CREATOR_MIN_TICKETING_WINDOW=PT1M \
  --set env.CREATOR_BATCH_WAITING_LEAD=PT30S \
  --set env.CREATOR_BATCH_COMPLETED_TOLERANCE=PT1M

helm upgrade streaming-service ./k8s/charts/microservice \
  -f k8s/values/values-streaming.yaml -n dev \
  --set env.STREAMING_LOBBY_LEAD=PT30S \
  --set env.STREAMING_SOON_LEAD=PT10S \
  --set env.STREAMING_POST_GRACE=PT1M
```

각 helm upgrade 실행 시 ConfigMap checksum 이 바뀌어 deployment.yaml 의 `checksum/configmap` annotation 갱신 → Pod 자동 rolling restart. 약 1~2분 후 시연 시작 가능.

상태 확인:
```bash
kubectl rollout status deployment/ticket-service-microservice -n dev
kubectl rollout status deployment/creator-service-microservice -n dev
kubectl rollout status deployment/streaming-service-microservice -n dev
```

## 4. 시연 직후 — 운영값 복귀

`--set` 없이 다시 올리면 `application-prod.yaml` 의 default(=운영값)로 폴백.

```bash
helm upgrade ticket-service ./k8s/charts/microservice -f k8s/values/values-ticket.yaml -n dev
helm upgrade creator-service ./k8s/charts/microservice -f k8s/values/values-creator.yaml -n dev
helm upgrade streaming-service ./k8s/charts/microservice -f k8s/values/values-streaming.yaml -n dev
```

## 5. 주의사항

- **이미 등록된 schedule 의 Quartz job 은 재계산되지 않는다.** Quartz trigger 시각은 `ScheduleEventListener.handleScheduleInitialized` 가 schedule 생성 시점에 한 번만 계산해서 jobStore 에 박는다. 시연 직전 ENV 를 짧게 바꿔도 **그 ENV 적용 후 새로 만든 schedule** 만 짧은 오프셋이 적용됨. 따라서 시연 절차는: helm upgrade → Pod 재기동 완료 → schedule 등록 → 시연.
- `creator-service ScheduleBatchService` 는 `@Scheduled(fixedRate=60_000)` 로 매분 폴링이라 새 ENV 를 즉시 따른다 (이미 SCHEDULED 상태인 schedule 도 적용됨).
- `creator-service ScheduleManageService` 의 등록/확정 검증은 호출 시점에 `@Value` 주입값을 사용 — ENV 적용 후 즉시 짧은 schedule 등록 가능.
- 시연용 값을 너무 짧게 잡으면 LOBBY → STREAMING 사이가 1분 미만이 되어 LOBBY UI 를 보여줄 시간이 없을 수 있다. 위 권장값은 LOBBY 30초 / SOON 10초 기준.
- daily ticket batch (`DailyTicketFeeProvideScheduler`, 1:00 AM cron) 는 시연 범위 밖 — 변경되지 않음.
- 핵심 비즈니스 로직(상태 머신, Kafka 토픽, Redis 키, 결제·환불·queue.drain 흐름) 은 변경되지 않았다.

## 6. 변경 파일 (참고)

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