# 0003. sessionToken 은 HS256 대칭키 (JJWT)

- **Status**: Accepted
- **Date**: 2026-04-21
- **Deciders**: 소유자

## Context

sessionToken 은 HLS `?t=` 쿼리파라미터와 STOMP CONNECT `token` 헤더로 전달되는 자체 발급 JWT 다 (TOKEN.md §1~§2). 이 토큰은:

- `streaming-service` 만 서명·검증한다 (gateway / creator / ticket 은 관여 안 함)
- 영상 재생 구간 동안만 유효 (`exp = endTime + 10m`)
- 매니페스트·세그먼트 요청마다 검증됨 → **검증 속도가 핵심**
- 토큰 개수가 폭증 (스케줄 × 유저 수)

서명 알고리즘 후보:
- **RS256** — 비대칭, 공개키 배포. `streaming-service` 만 검증하는데 공개키 분리의 이점이 거의 없다.
- **HS256** — 대칭, 단일 공유 비밀. 검증 속도 약 3~5배 빠름, 키 관리 단순.

## Decision

sessionToken 은 **HS256 대칭키** 로 서명한다. 라이브러리는 JJWT (io.jsonwebtoken:jjwt-*) 를 채택한다.

- 비밀 키: `streaming.jwt.secret` (env `STREAMING_JWT_SECRET`), 최소 32 바이트 랜덤.
- 페이로드: `sub=sessionId(UUID)`, `exp=endTime+10m`, `iat=now`, `iss="streaming-service"`.
- 어댑터: `JjwtStreamTokenAdapter` 가 `StreamTokenPort` 구현.

```java
public interface StreamTokenPort {
    String issue(UUID sessionId, Instant expiresAt);
    UUID parse(String token) throws StreamTokenException;
}
```

## Consequences

- **Positive**:
  - HLS 세그먼트 요청마다 서명 검증이 발생하므로 검증 속도 이득이 실질적 — HS256 이 RS256 보다 약 3~5x 빠름.
  - 키 관리 단순화: 공개키 배포, 키 교체 절차, 비대칭 적재 불요.
  - JJWT 는 user-service 에서 이미 사용 중 — 팀 친숙도 확보.

- **Negative**:
  - 공유 비밀이 유출되면 토큰을 위조할 수 있다 — 단, `streaming-service` 단일 검증자이므로 **비밀은 이 서비스 외부로 절대 나가지 않는다**. 운영 측면에서 RS256 대비 위험 증가 없음.
  - prod 에서 secret 유출 감지 시 전 세션 무효화는 secret 교체 + 모든 Redis 세션 키 삭제로 강제.

- **Neutral**:
  - 추후 다른 서비스가 sessionToken 을 검증해야 하는 상황이 생기면 RS256 전환 고려 — 그때 `StreamTokenPort` 어댑터만 교체.

## Alternatives Considered

- **RS256 비대칭**: 다른 서비스 검증 가능성. 기각: 현재 이 토큰을 쓰는 서비스는 streaming-service 단 하나. 검증 속도 이득 없음.
- **Property 로 알고리즘 선택 (`streaming.jwt.algorithm=HS256|RS256`)**: 유연성 확보. 기각: 초기 단계 과도한 유연성. 필요 시 ADR 갱신으로 전환.

## Related

- `TOKEN.md` — sessionToken 전반 (발급·검증·수명)
- ADR 0005 — HLS 매니페스트 재작성 (토큰 주입 지점)
- ADR 0010 — CONNECT 1회 검증 + session attribute 캐시
- `API.md §3.2` — STOMP CONNECT header 로 토큰 전송
