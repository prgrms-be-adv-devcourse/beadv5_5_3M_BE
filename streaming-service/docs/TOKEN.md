# streaming-service 토큰 설계

이 문서는 streaming-service가 다루는 **토큰의 종류 · 발급 · 검증 · 수명**을 정리한다. 다른 설계 문서(`DESIGN.md`)의 단편적인 서술을 한 곳에 모아 구현 시 참조할 수 있게 한 것.

상위 개요는 `OVERVIEW.md`, 전체 아키텍처와 플로우는 `DESIGN.md`, 실제 구현은 `infrastructure/token/JjwtStreamTokenAdapter.java` 참조.

---

## 1. 시스템의 토큰 지도

streaming-service를 포함한 MSA 전체에서 발급되는 JWT·토큰:

| 토큰 | 발급자 | 검증자 | 발급 시점 | 수명 |
|---|---|---|---|---|
| **유저 액세스 JWT** | `user-service` | `gateway-service` (RSA 공개키) | 유저 로그인 | 액세스 토큰 주기 (분~시간) |
| **유저 리프레시 토큰** | `user-service` | `user-service` | 유저 로그인 | 길게 (일~주) |
| **크리에이터 액세스 JWT** | `creator-service` | `gateway-service` (별도 RSA 공개키) | 크리에이터 로그인 | 액세스 토큰 주기 |
| **크리에이터 리프레시 토큰** | `creator-service` | `creator-service` (Redis `refresh:token:<creatorId>`) | 크리에이터 로그인 | 길게 |
| **스트리밍 sessionToken** | `streaming-service` | `streaming-service` | `POST /api/streaming/sessions` | `endTime + 10m` 까지 |

**핵심 원칙**:
- **각 도메인 서비스가 자기 토큰을 발급한다.** gateway는 오직 검증자.
- sessionToken도 예외가 아님 — streaming-service가 자체 서명하고 자체 검증한다.
- gateway는 sessionToken의 존재조차 모른다 (쿼리 파라미터·STOMP 헤더로 실려오므로 gateway 필터는 통과).

이 문서는 이 중 **sessionToken에만** 집중한다. 게이트웨이 JWT는 user-service·creator-service의 문서를 참조.

---

## 2. sessionToken 개요

### 2.1 왜 게이트웨이 JWT와 별도로 발급하는가

게이트웨이 JWT 하나로 HLS·WS까지 처리하지 않은 이유는 세 가지 요구사항이 JWT만으로는 충족되지 않기 때문:

| 요구사항 | 게이트웨이 JWT 단독으로 가능한가 |
|---|:-:|
| 상영 종료(`endTime + 10m`)에 서버가 **강제로 무효화** | ✖ (stateless JWT는 서버측 revocation 불가) |
| 한 유저가 **동시 1세션**만 보도록 제한 · 새 세션이 옛 세션 kick | ✖ (토큰 자체로 식별 불가) |
| HLS 세그먼트가 **쿼리 파라미터로 토큰을 실어야** 함 (`?t=...`) — 유출 시 피해를 특정 스케줄·특정 시간대로 제한 | ✖ (게이트웨이 JWT를 URL에 박으면 유저 전체 권한이 노출) |

sessionToken은 이 세 가지를 모두 해결하는 **좁은 scope의 capability 토큰**이다.

### 2.2 sessionToken의 성질

| 속성 | 값 |
|---|---|
| 타입 | JWT |
| payload `sub` | `sessionId` (서버가 생성한 UUID v4) |
| payload `exp` | `endTime + 10m` (스케줄 종료 + 10분) |
| 서명 알고리즘 | HS256 (대칭 키) 또는 RS256 (비대칭) — `StreamTokenPort` 어댑터가 결정 |
| stateless? | **아니오**. Redis `stream:session:id:{sessionId}` 키가 존재해야만 유효. JWT 서명 + Redis 존재 확인이 **AND** |
| revocation | `DEL stream:session:id:{sessionId}` — 즉시 무효화 |
| 전달 위치 (HLS) | 쿼리 파라미터 `?t={sessionToken}` |
| 전달 위치 (WS) | STOMP CONNECT 헤더 `token={sessionToken}` |

### 2.3 sessionId vs sessionToken

둘은 다른 층위의 값이다:

- **sessionId** = 서버 내부 식별자 (UUID). Redis 키 `stream:session:id:{sessionId}` · `stream:session:user:{userId}.sessionId` 필드에 저장.
- **sessionToken** = sessionId를 `sub`에 담아 서명한 JWT. 클라이언트에게 전달되는 것은 이것뿐.

**클라이언트는 sessionToken만 보관한다.** sessionId는 클라이언트 입장에서 "sessionToken 안에 숨어있는 내부 식별자". API 호출 시 요구되지도 않는다.

---

## 3. 라이프사이클

```
[발급]  POST /api/streaming/sessions
          │
          ├─ EnterStreamService.execute()
          │    ├─ 검증: entitlement · canEnterSession · 기존 세션 kick
          │    ├─ sessionId = UUID.randomUUID()
          │    ├─ sessionToken = StreamTokenPort.issue(sessionId, endTime+10m)
          │    ├─ Redis SET stream:session:id:{sessionId}   TTL endTime+10m
          │    └─ Redis SET stream:session:user:{userId}    TTL endTime+10m
          │
          └─ Response: { sessionToken, manifestUrl, wsEndpoint, ... }

[사용]  GET /api/streaming/{id}/index.m3u8?t={sessionToken}
          │
          └─ HlsServingService.serve()
               ├─ sessionId = StreamTokenPort.parse(sessionToken)
               ├─ Redis GET stream:session:id:{sessionId}   → 없으면 SESSION_EXPIRED
               ├─ session.scheduleId == request.scheduleId  → 아니면 SESSION_MISMATCH
               └─ canServeHls(now) 재확인 · 파일 서빙

        STOMP CONNECT ws://.../ws/stream   Header: token={sessionToken}
          │
          └─ StompAuthChannelInterceptor.preSend(CONNECT)
               ├─ sessionId = StreamTokenPort.parse(sessionToken)
               ├─ Redis GET stream:session:id:{sessionId}
               └─ 주체 attribute 설정 (userId, scheduleId, sessionId, wsSessionId)

[소멸]  세 가지 경로
          (a) 자연 만료: JWT exp 도달 + Redis TTL 자동 정리 (endTime+10m)
          (b) 명시적 kick: 새 세션 생성 시 플로우 C (4)에서 DEL stream:session:id:{oldSessionId}
          (c) 강제 퇴장: ForceExitJob이 해당 스케줄의 모든 session 키 DEL
```

**유저가 토큰을 몇 개 들고 있는가**:

| 상태 | 보유 토큰 |
|---|---|
| 로그인 전 | 0개 |
| 로그인만 한 상태 | 1개 (게이트웨이 JWT) |
| 활성 스트리밍 세션 | **2개** (게이트웨이 JWT + sessionToken) |
| 상영 종료 후 | 1개 (sessionToken은 Redis에서 증발 → 사실상 사망) |

---

## 4. 추상화: `StreamTokenPort`

sessionToken의 발급·검증을 **출력 포트**로 분리한다. D6(ticket-service 스타일)의 포트-어댑터 컨벤션을 따른다.

### 4.1 인터페이스

```java
// application/port/StreamTokenPort.java
public interface StreamTokenPort {

    /** Flow C (5)에서 호출. sessionId와 만료 시각을 받아 서명된 JWT 문자열을 반환. */
    String issue(UUID sessionId, Instant expiresAt);

    /** Flow D (1) · Flow E (1)에서 호출.
     *  서명 검증 + exp 확인 후 sessionId 반환. 실패 시 StreamTokenException 던짐. */
    UUID parse(String token) throws StreamTokenException;
}
```

### 4.2 사용처

| 사용처 | 호출 | 실패 시 매핑 |
|---|---|---|
| `EnterStreamService` (§5.3 플로우 C) | `issue(sessionId, endTime+10m)` | — |
| `HlsServingService` (§5.4 플로우 D (1)) | `parse(token)` | `SESSION_EXPIRED` (401) |
| `StompAuthChannelInterceptor` (§5.5 플로우 E (1)) | `parse(token)` | CONNECT 실패 |

### 4.3 어댑터

MVP는 HS256 대칭 키 어댑터. 키 교체·비대칭 전환 시 어댑터 교체만으로 끝.

```java
// infrastructure/token/JjwtStreamTokenAdapter.java
@Component
@RequiredArgsConstructor
public class JjwtStreamTokenAdapter implements StreamTokenPort {

    private final StreamTokenProperties props;   // 서명 키 · 알고리즘
    private final Clock clock;

    @Override
    public String issue(UUID sessionId, Instant expiresAt) {
        return Jwts.builder()
                   .subject(sessionId.toString())
                   .expiration(Date.from(expiresAt))
                   .issuedAt(Date.from(clock.instant()))
                   .signWith(props.signingKey(), Jwts.SIG.HS256)
                   .compact();
    }

    @Override
    public UUID parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                                  .verifyWith(props.signingKey())
                                  .clock(() -> Date.from(clock.instant()))
                                  .build()
                                  .parseSignedClaims(token);
            return UUID.fromString(jws.getPayload().getSubject());
        } catch (ExpiredJwtException e) {
            throw new StreamTokenException(ErrorCode.SESSION_EXPIRED, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new StreamTokenException(ErrorCode.INVALID_TOKEN, e);
        }
    }
}
```

**교체 매트릭스**:

| 어댑터 | 서명 | 적합한 상황 |
|---|---|---|
| `JjwtStreamTokenAdapter` (HS256) | 대칭 키 | MVP. 단일 서비스 내 발급·검증 |
| `RsaStreamTokenAdapter` (RS256) | 비대칭 | 향후 외부 검증자가 필요한 경우 (예: CDN에서 직접 검증) |

---

## 5. 서명 키 관리

### 5.1 dev

- `application-dev.yaml` 에 키를 커밋해도 무방 (개발 전용).
- `streaming.jwt.secret: <hex 또는 base64>` 프로퍼티로 주입.

```yaml
streaming:
  jwt:
    algorithm: HS256
    secret: dev-only-do-not-use-in-prod-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 5.2 prod

- 시크릿은 **환경변수**로 주입. `STREAMING_JWT_SECRET`.
- HS256 기준 최소 32 바이트 랜덤. RS256 전환 시 `STREAMING_JWT_PRIVATE_KEY`.

```yaml
streaming:
  jwt:
    algorithm: ${STREAMING_JWT_ALGORITHM:HS256}
    secret: ${STREAMING_JWT_SECRET}
```

### 5.3 키 로테이션

sessionToken 수명이 짧아(`endTime + 10m`, 최대 수 시간) 롤오버 중단이 작음:
1. 새 키 배포 (두 키를 동시에 허용하는 "dual key" 기간 설정)
2. 신규 발급은 새 키로
3. 구 키로 발급된 토큰이 모두 만료되면 구 키 제거

MVP에서는 단일 키. 멀티 키 지원은 필요 시점에 추가.

---

## 6. 에러 매핑

`parse()` 실패가 상위 컨트롤러·인터셉터에서 어떻게 응답으로 전환되는지:

| 발생 지점 | 원인 | ErrorCode | 응답 |
|---|---|---|---|
| `HlsServingService` | JWT 서명 불일치 / 형식 오류 | `INVALID_TOKEN` | 401 |
| `HlsServingService` | JWT `exp` 지남 | `SESSION_EXPIRED` | 401 |
| `HlsServingService` | Redis에 session 없음 (JWT는 유효) | `SESSION_EXPIRED` | 401 |
| `HlsServingService` | 토큰의 scheduleId ≠ request scheduleId | `SESSION_MISMATCH` | 409 |
| `StompAuthChannelInterceptor` | 위 중 하나 | — | CONNECT 실패 + WS close |

`DESIGN.md §6 에러 모델`의 항목과 1:1 대응. 서명 불일치와 `exp` 만료는 클라이언트 입장에서 "세션을 다시 만들라"는 동일한 지시로 귀결되므로 사용자 UX는 같다.

---

## 7. 보안 고려사항

### 7.1 URL 쿼리 파라미터의 토큰 유출

HLS 요청이 `?t={sessionToken}` 형식이므로:

| 유출 경로 | 대응 |
|---|---|
| 접근 로그에 토큰이 그대로 기록 | Nginx/액세스 로그에서 `t=` 쿼리 **마스킹** 설정 필수 |
| `Referer` 헤더로 외부 도메인에 유출 | HLS 요청은 `<video>` 태그의 미디어 요청으로 대부분 동일 출처. 페이지에 외부 리소스 임베드 시 `Referrer-Policy: no-referrer` 또는 `same-origin` |
| 브라우저 히스토리 | HLS 세그먼트 URL은 일반적으로 히스토리에 안 남음 (페이지 네비게이션 아님) |
| 브라우저 개발자 도구의 Network 탭 | 원천 차단 불가. sessionToken의 **좁은 scope와 짧은 TTL** 이 피해를 제한하는 근거 — 게이트웨이 JWT를 쿼리에 싣지 않는 이유 |

### 7.2 CSRF

sessionToken은 쿠키가 아니라 **쿼리 파라미터 · STOMP 헤더**로 전달되므로 CSRF 영향 없음 (브라우저가 자동으로 싣지 않음).

### 7.3 XSS로 인한 토큰 탈취

sessionToken을 localStorage에 저장하면 XSS에 노출. 클라이언트 권고:
- **메모리에만** 보관 (React state, 클래스 필드 등)
- 새로고침 시 재발급 (`POST /api/streaming/sessions` 다시 호출 → 기존 세션 kick 후 신규 발급)
- localStorage·sessionStorage·쿠키 저장 비권장

### 7.4 토큰 재사용 공격

JWT 서명이 유효해도 Redis 키가 지워지면 무효. `ForceExitJob`이나 새 세션 생성 시 옛 토큰이 즉시 사망하므로, 탈취된 토큰도 상영 종료 또는 kick 순간 자동 만료.

---

## 8. 요약

- **sessionToken은 streaming-service가 발급·검증하는 자체 JWT.** 게이트웨이 JWT와 별개.
- **gateway는 sessionToken을 모른다.** 쿼리·STOMP 헤더로 실려오므로 gateway 필터 통과.
- **발급 1회 / 사용 N회**: `POST /api/streaming/sessions` 시 한 번 발급, HLS 세그먼트 요청과 WS CONNECT에서 재사용.
- **이중 방어**: JWT 서명 유효성 AND Redis 키 존재. 두 가지가 모두 성립해야 토큰이 살아있다.
- **발급·파싱은 `StreamTokenPort` 어댑터 한 곳에.** 세 호출처(`EnterStreamService` · `HlsServingService` · `StompAuthChannelInterceptor`)가 공유.
- **클라이언트는 sessionToken만 안다.** sessionId는 서버 내부 식별자.