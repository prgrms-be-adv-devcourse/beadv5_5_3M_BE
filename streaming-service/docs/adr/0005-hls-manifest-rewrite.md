# 0005. HLS 세그먼트 인증: 매니페스트 동적 재작성

- **Status**: Accepted
- **Date**: 2026-04-21
- **Deciders**: 소유자

## Context

HLS 는 클라이언트가 매니페스트(`index.m3u8`)를 받아 세그먼트(`segment_001.ts`) 를 순차 요청하는 구조다. 세그먼트 요청에 토큰을 싣는 방법:

1. **HttpOnly 쿠키**: 매니페스트 응답 시 쿠키 세팅 → 이후 세그먼트 요청에 자동 동반. 단, HLS 플레이어(video.js, hls.js)가 Cross-Origin 환경에서 쿠키 전달을 일관되게 보장하지 못함. `withCredentials` 세팅이 플레이어마다 다름.
2. **매니페스트 재작성 + URL 쿼리**: 매니페스트의 각 세그먼트 URL 줄에 `?t={sessionToken}` 을 주입해서 반환. 클라이언트는 그 URL 을 그대로 요청.
3. **세그먼트 미인증**: 매니페스트만 인증하고 세그먼트는 open. 토큰 만료 이후에도 세그먼트 URL 이 유효하면 우회 가능.

제약:
- 브라우저 HLS 플레이어 범용성 필요 (Safari native, video.js, hls.js 전부).
- 세그먼트 URL 유출 시에도 재인증 차단되어야 함 (토큰 만료 시 차단).
- 토큰은 JWT 라 길이가 큼 — URL 안에 들어가도 HTTP 라인 한도는 넘지 않음.

## Decision

매니페스트를 **동적으로 재작성**해서 각 세그먼트 줄에 `?t={sessionToken}` 을 주입한다.

재작성 알고리즘 (의사코드, 실구현은 `infrastructure/websocket/ManifestRewriter.java`):

```
for each line in manifest:
  if line.startsWith("#") OR line.isBlank():
    emit as-is
  else:
    fileName = line.substring(line.lastIndexOf('/') + 1)
    emit publicBaseUrl + "/api/streaming/" + scheduleId + "/" + fileName + "?t=" + token
```

- 응답 `Content-Type: application/vnd.apple.mpegurl`
- 응답 `Cache-Control: no-store` (토큰이 URL 에 있으므로 캐시 금지)
- 세그먼트 요청도 같은 `StreamTokenPort.parse()` 로 검증 + Redis 세션 일치 체크.

## Consequences

- **Positive**:
  - 모든 HLS 플레이어 호환 (쿠키 설정 의존성 없음).
  - 토큰 만료 시 세그먼트 요청도 자동으로 차단.
  - 매니페스트 요청 1회마다 재작성 1회 — 비용 작음.

- **Negative**:
  - 매니페스트 캐시 불가 (토큰이 URL 안에 있으므로). `Cache-Control: no-store` 필수.
  - 토큰이 URL 에 노출되어 서버 로그/프록시 로그에 찍힘 — 노출 범위를 내부 인프라로 한정해야 함.
  - 매니페스트 재작성 로직 테스트 필수 (절대 경로 / 상대 경로 / 라인별 prefix 주석 혼재).

- **Neutral**:
  - 추후 CDN 도입 시 서명 URL 로 전환 가능 — `StreamAddressPort` 어댑터 교체로 대응 (`DESIGN.md §4.6`).

## Alternatives Considered

- **HttpOnly 쿠키**: 플레이어 호환성 문제로 기각.
- **세그먼트 미인증**: 토큰 만료 후에도 URL 만 알면 세그먼트를 받을 수 있어 유료 컨텐츠 보호 실패. 기각.
- **HLS AES-128 암호화 key URI 에 토큰 주입**: 가능하지만 복잡도 큼. 현 단계 불요.

## Related

- ADR 0003 — HS256 토큰
- `infrastructure/websocket/ManifestRewriter.java` — 실제 재작성 구현
- `API.md §2` — `GET /api/streaming/{scheduleId}/{file}` 스펙
- `TOKEN.md §3` — 토큰 매체별 전달 방식
