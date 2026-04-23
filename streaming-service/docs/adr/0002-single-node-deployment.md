# 0002. 단일 노드 배포 (Quartz 비클러스터)

- **Status**: Accepted
- **Date**: 2026-04-21
- **Deciders**: 소유자

## Context

`streaming-service` 는 WebSocket 연결·Quartz 스케줄러·단일 세션 Redis enforcement 를 모두 소유한다. 다중 인스턴스로 배포하면 다음 문제가 발생한다:

- **Quartz**: 스케줄 생성 시 등록되는 6 Job 이 각 인스턴스에서 중복 실행되거나, `isClustered=true` 설정을 위해 공용 Quartz 스키마(QRTZ_* 11 테이블)와 Row 락 트랜잭션이 필요하다.
- **WebSocket**: 특정 스케줄에 구독된 유저가 여러 인스턴스에 분산되면 `chat`/`viewers`/`state` broadcast 를 인스턴스 간 전파해야 한다 (Redis Pub/Sub 또는 STOMP RabbitMQ external broker 도입 필요).
- **단일 세션 kick-old**: 새 세션 발급 인스턴스가 기존 세션이 있는 인스턴스에게 kick 메시지를 전파해야 한다.

반면 라이브 동시 시청자 수의 현실적 상한은 초기 몇 백~몇 천 명 수준이고, 초기 단계에서 수평 확장의 복잡도 비용은 과도하다.

## Decision

단일 노드 배포를 가정한다.

- Quartz: `spring.quartz.job-store-type=jdbc`, `properties.org.quartz.jobStore.isClustered=false`, 메모리/JDBC 어느 쪽이든 단일 스케줄러 인스턴스로 충분.
- WebSocket: Spring 의 SimpleBroker (`@EnableSimpleBroker`) 사용. 외부 broker(RabbitMQ/ActiveMQ) 불요.
- 단일 세션: Redis `stream:session:user:{userId}` 하나를 source of truth 로 삼고, 새 세션 발급 시 직전 세션에 `/user/queue/kick` 메시지를 현재 JVM 내 WebSocket 세션으로 직접 전달.
- ForceExitJob 의 Redis 일괄 삭제는 단순 `SCAN` 배치로 처리 (수천 명 이하 가정).

## Consequences

- **Positive**:
  - 인프라와 코드 모두 단순. 외부 broker, Quartz 클러스터 스키마, Pub/Sub 모두 불요.
  - 디버깅 용이 — 모든 상태가 한 JVM + Redis/DB 에 있음.
  - Quartz Job 의 중복 실행 문제 자체가 없음.

- **Negative**:
  - 인스턴스 장애 시 전체 서비스 중단 (초기 단계 수용 가능).
  - 수직 확장 상한에 도달하면 재설계가 필요 (분산 Quartz + 외부 broker + Redis Pub/Sub kick-old).

- **Neutral**:
  - 장래 수평 확장 시 필요한 대안은 아래 "Alternatives Considered" 에 기록돼 있어 참조 가능.

## Alternatives Considered

- **Quartz 클러스터 + 외부 broker + Redis Pub/Sub**: 수평 확장 가능. 기각: 초기 트래픽에서 운영 복잡도 대비 이득 없음.
- **Stateless 서버 + sticky session 로드밸런서**: WebSocket 은 sticky 로 해결되지만 Quartz 중복 실행과 단일 세션 Redis enforcement 는 해결 안됨. 부분 해결이라 기각.

## Related

- `DESIGN.md` — 배포 및 확장성 노트
- `ARCHITECTURE.md §6` — 수평 확장 시 고려 사항
