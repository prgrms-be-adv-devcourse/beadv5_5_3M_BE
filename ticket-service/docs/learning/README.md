# ticket-service 단계별 학습 자료

각 Stage는 원본 문서/소스에서 필요한 부분만 **발췌·압축**한 학습용 자료입니다.
각 파일은 독립적으로 읽을 수 있으며, 원본 위치는 문서 내에 표기했습니다.

## 학습 순서

| 순서 | Stage | 주제 | 예상 소요 |
|------|-------|------|-----------|
| 1 | [Stage 0](stage-00-orientation.md) | 전체 그림 (아키텍처·용어·의존성) | 0.5일 |
| 2 | [Stage 1](stage-01-domain.md) | 도메인 모델 (엔티티·Enum·상태머신) | 0.5일 |
| 3 | [Stage 2](stage-02-application-services.md) | 애플리케이션 서비스 (유스케이스) | 2~3일 |
| 4 | [Stage 3](stage-03-ports-adapters.md) | 출력 포트 & 어댑터 | 1일 |
| 5 | [Stage 4](stage-04-event-listeners.md) | 이벤트 리스너 (AFTER_COMMIT) | 1.5일 |
| 6 | [Stage 5](stage-05-quartz-kafka.md) | Quartz Job + Kafka Consumer | 1일 |
| 7 | [Stage 6](stage-06-spring-batch.md) | Spring Batch | 0.5일 |
| 8 | [Stage 7](stage-07-concurrency-deep.md) | 동시성 심화 (Queue & Stock) | 2일 |
| 9 | [Stage 8](stage-08-tests-e2e.md) | 테스트 & E2E | 1일 |
| 10 | [Stage 9](stage-09-integration-review.md) | 통합 회고 | 0.5일 |

## 읽는 방법

1. **먼저 본문을 쭉 읽고** 개념과 코드 발췌를 머릿속에 넣는다.
2. **하단의 ★ 질문**에 스스로 답을 적어본다. 막히면 "원본 참고" 링크로 돌아가 확인.
3. **마지막 체크리스트**로 이 Stage를 통과했는지 확인하고 다음 Stage로.

`★`은 "이 질문에 답하지 못하면 다음 Stage로 넘어가면 안 되는" 핵심 질문.