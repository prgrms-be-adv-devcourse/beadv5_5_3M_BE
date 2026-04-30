# [BE → FE 공유] ticket-service: 장바구니 조회 응답에 imageUrl 추가

작성일: 2026-04-30
대상 변경 위치: `ticket-service/src/main/java/com/example/ticketservice/application/dto/response/CartItemResponse.java`

## 무엇이 바뀌었나
`GET /api/cart` 응답 DTO `CartItemResponse` 에 `imageUrl` 필드가 추가됩니다.
포스터 이미지 URL (`Schedule.imageUrl` 그대로) 을 그대로 내려줍니다.

## 왜 바뀌나
FE 측 타입(`cinestream_fe/src/app/services/ticketService.ts:7-16`)에는 이미 `imageUrl: string | null` 이
선언되어 있었으나, BE DTO 에 해당 필드가 누락되어 실제 응답 JSON 에는 키 자체가 빠져 내려가던
상태였습니다. BE 의 누락이었으며, 이번 변경으로 BE/FE 계약이 맞춰집니다.

## API 동작 변화
응답 예:

```json
[
  {
    "scheduleId": 1,
    "movieId": 10,
    "title": "...",
    "imageUrl": "https://.../poster.jpg",
    "startTime": "...",
    "endTime": "...",
    "ticketingTime": "...",
    "cookie": 5
  }
]
```

값이 없는 스케줄의 경우 `null` 로 내려갑니다 (`Schedule.imageUrl` 이 nullable 이라 그대로 통과).

## FE 가 해야 할 일
- 타입 변경 **불필요** — `CartItemResponse.imageUrl: string | null` 가 이미 선언되어 있어 그대로 동작합니다.
- 그동안 placeholder 또는 별도 fetch 로 대체했다면, 이제 응답값을 직접 사용해도 됩니다.
- 사용 예: `CartPage.tsx` 의 카트 카드, `Header` 의 카트 카운트 옆 미리보기 등.

## 영향 범위
- 깨질 만한 곳: 없음 (필드 추가만, 기존 키/값 변경 없음)
- 잠재적 개선: `CartPage` 에서 `movieService` 로 별도 enrich 하던 부분이 있다면 단순화 가능

## 변경된 BE 파일
- `ticket-service/src/main/java/com/example/ticketservice/application/dto/response/CartItemResponse.java` — record 에 `imageUrl` 필드 + `from()` 매핑 추가
