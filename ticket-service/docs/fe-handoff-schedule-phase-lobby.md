# [BE → FE 공유] ticket-service: SchedulePhase enum 값 확장

작성일: 2026-04-30
대상 변경 위치: `ticket-service/src/main/java/com/example/ticketservice/domain/enums/SchedulePhase.java`

## 무엇이 바뀌었나
ticket-service 의 `SchedulePhase` enum 에 값 3개가 추가됩니다.

| 추가된 phase 값 | 매핑되는 ScheduleStatus | 의미 |
|----------------|------------------------|------|
| `LOBBY_WAITING` | `LOBBY` | 티켓팅 종료 후 스트리밍 직전 대기 (startTime - 10분 ~ startTime) |
| `STREAMING_PERIOD` | `STREAMING` | 상영 중 |
| `FINISHED` | `FINISH` | 종료된 회차 |

기존 값(`CART_PERIOD`, `PROVISIONAL_PAYMENT`, `TICKETING_PERIOD`)과 매핑은 그대로입니다.

## 왜 바뀌나
지금까지는 `SchedulePhase.from()` switch 가 LOBBY/STREAMING/FINISH 를 매핑하지 못해
`GET /api/tickets/schedules/open` 가 LOBBY 상태 스케줄을 만나면 500 을 반환하던 버그가 있었습니다.
이번 변경으로 6개 status 전부가 phase 로 안전하게 변환됩니다.

또한 switch 의 `default` 분기를 제거하고 Java 21 exhaustive switch 로 전환하여,
앞으로 `ScheduleStatus` 에 새 값이 추가되면 컴파일 시점에 즉시 인지할 수 있도록 강제했습니다.

## API 동작 변화

엔드포인트: `GET /api/tickets/schedules/open`

응답 DTO `TicketableScheduleResponse` 의 `phase` 필드에 다음 신규 값이 실릴 수 있습니다:
- `phase: "LOBBY_WAITING"` (status=LOBBY 인 경우)
- (정책상 STREAMING/FINISH 는 OPEN 응답 범위에 포함되지 않으므로 실전에서는 거의 보이지 않지만, enum 자체에는 존재함)

`status: "LOBBY"` 자체는 이미 응답에 포함되고 있었습니다 (FE 도 이미 처리 중 — `TicketableScheduleCard.tsx`, `MovieDetailPage.tsx`).

## FE 가 해야 할 일

`cinestream_fe/src/app/services/ticketService.ts` 의 `SchedulePhase` 유니온 타입을 다음과 같이 확장해주세요:

```ts
export type SchedulePhase =
  | "CART_PERIOD"
  | "PROVISIONAL_PAYMENT"
  | "TICKETING_PERIOD"
  | "LOBBY_WAITING"        // NEW
  | "STREAMING_PERIOD"     // NEW
  | "FINISHED";            // NEW
```

UI 분기 추가는 **불필요**합니다. 현재 FE 코드베이스를 audit 한 결과, `phase` 필드를 read 하는
컴포넌트가 한 곳도 없고 모든 분기가 `status` 기반으로 동작하기 때문입니다.
타입 동기화만 해주시면 향후 phase 활용 시 안전합니다.

## 컨트롤러 문서 업데이트
`@Parameter` description 도 `(CART, IN_PROGRESSING, TICKETING, LOBBY)` 로 정정되어
Swagger UI 의 안내가 실제 동작과 일치합니다. 기존엔 LOBBY 가 누락되어 있었습니다.

## 영향 범위
- 깨질 만한 곳: 없음 (FE 가 phase 를 read 하지 않음)
- 잠재적 개선: phase 필드를 활용해 LOBBY 동안 별도 안내 ("곧 시작" 등) 표시 가능 — 현재는 status 기반으로 처리 중이라 그대로 둬도 무방

## 변경된 BE 파일 목록 (참고)
- `ticket-service/src/main/java/com/example/ticketservice/domain/enums/SchedulePhase.java` — enum 값 3개 추가 + switch exhaustive 전환
- `ticket-service/src/main/java/com/example/ticketservice/presentation/controller/ScheduleController.java` — `@Parameter` description LOBBY 포함으로 정정
- `ticket-service/src/main/java/com/example/ticketservice/application/dto/response/MovieScheduleResponse.java` — JavaDoc 정정 + phase 부재 이유 명시