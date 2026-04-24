# Architecture Decision Records

`streaming-service` 의 **핵심** 결정 5건을 ADR 형식으로 남긴다. 각 ADR 은 **Context · Decision · Consequences · Alternatives Considered · Related** 5 절로 구성되며 `Accepted` 상태만 포함한다.

> **슬림화 정책**: MVP 시점에 결정된 13개 ADR 중 운영·확장·보안에 지속 영향을 주는 **5개만 별도 유지**. 나머지 8개(스코프 결정·채팅 휘발성·StreamAddressPort·로컬 업스트림 실물 구동·릴리즈 범위·STOMP 헤더 토큰·채팅 rate limit·익명 닉네임)의 결정 내용은 `DESIGN.md`, `API.md`, `DOMAIN-MODEL.md`, `RUNBOOK.md` 등 해당 주제 문서에 1~2 줄로 흡수되었다.

## 목록

| # | 파일 | 결정 요약 | 왜 유지하는가 |
|---|---|---|---|
| 0002 | [single-node-deployment](0002-single-node-deployment.md) | 단일 노드 가정, Quartz `isClustered=false`, SimpleBroker | 수평 확장 시 재설계 필요 — 운영 제약 |
| 0003 | [hs256-session-token](0003-hs256-session-token.md) | sessionToken HS256 대칭키, JJWT | 보안·토큰 알고리즘 선택 근거 |
| 0005 | [hls-manifest-rewrite](0005-hls-manifest-rewrite.md) | 세그먼트 인증: 매니페스트 동적 재작성으로 `?t=` 주입 | 코어 인증 메커니즘 |
| 0010 | [entitlement-on-connect](0010-entitlement-on-connect.md) | STOMP CONNECT 1회 검증 + session attribute 캐시 | WS 성능 최적화 패턴 |
| 0012 | [immediate-forceexit](0012-immediate-forceexit.md) | ForceExit 즉시 kick + WS close + Redis 삭제 | 강제 퇴장 UX·리소스 정리 정책 |

## 상태 요약

- 5 건 모두 `Accepted`.
- `Superseded` / `Deprecated` 는 해당 결정을 뒤집는 새 ADR 을 작성하며 이 표와 원 파일의 상태를 갱신.

## 관련 문서

- [ARCHITECTURE.md](../ARCHITECTURE.md) — 포트폴리오용 원페이지 요약
- [OVERVIEW.md](../OVERVIEW.md) — 서비스 개요
- [DESIGN.md](../DESIGN.md) — 설계 결정 표(§3) · 플로우 다이어그램
- [TOKEN.md](../TOKEN.md) — sessionToken 상세 (ADR 0003/0005)
- [API.md](../API.md) — WS CONNECT (ADR 0010)
- [DOMAIN-MODEL.md](../DOMAIN-MODEL.md) — 엔티티 필드·메서드
- [MESSAGE-SCHEMAS.md](../MESSAGE-SCHEMAS.md) — Kafka payload
- [RUNBOOK.md](../RUNBOOK.md) · [CONFIG.md](../CONFIG.md)
