# Stage 7 — 동시성 심화 (Queue & Stock)

> **목표**: `QueueAutoProcessService`와 `QueuePurchaseProcessor`를 **한 줄씩** 읽으며 race condition 5개 시나리오를 타임라인으로 재구성한다.
> 이 서비스에서 **가장 버그 나기 쉬운 부분**. 여기를 손에 익히지 않으면 앞으로 어떤 기능을 추가하든 미묘한 불일치가 반복된다.
> **예상 소요**: 2일

---

## 0. 이 Stage만의 독해 방식

- 글보다 **타임라인 그림** 위주. 화이트보드 or A4 종이에 t=0, 10ms, 20ms… 시각 축 그리고 스레드 A/B를 세로로 놓는 방식.
- 각 시나리오마다: ① 초기 상태 → ② 스레드별 명령 → ③ 엣지에서 어떤 race가 가능한가 → ④ 코드가 이 race를 어떻게 막는가.
- 정답을 읽기 전에 **자기 먼저** 타임라인을 그려보고, 그 다음 코드와 비교할 것.

---

## 1. 핵심 원자 연산 4개

본격적인 시나리오 전에 이 4개가 **Redis에서 원자적**임을 고정 전제로 삼는다.

| 연산 | 의미 | 왜 원자적인가 |
|------|------|--------------|
| `DECR key` | 카운터 1 감소, 감소 후 값 반환 | Redis 단일 커맨드 = single-threaded event loop |
| `INCR key` | 카운터 1 증가, 증가 후 값 반환 | 위와 동일 |
| `ZPOPMIN key` | sorted set에서 가장 낮은 점수 원소 **꺼내면서 삭제** | 단일 커맨드에 "읽기 + 삭제"가 합쳐짐 |
| `DEL key` | 키 삭제, 존재했으면 true/1 반환 | 단일 커맨드, 반환값으로 "내가 지운 첫 스레드" 판정 가능 |

여러 커맨드 조합(예: `GET` 후 `SET`)은 원자적이지 **않다**. 이 서비스가 race를 회피하는 주된 패턴은 **"DECR이 음수를 반환하면 복구"**.

---

## 2. DECR-first 패턴 — 왜 먼저 감소시키고 맞는지 검사하는가

**잘못된 패턴 (GET-then-DECR)**:
```
if (stock > 0) {       ← 스레드 A, B 둘 다 true 봄
    DECR stock          ← 둘 다 실행 → 오버부킹
}
```
`GET`과 `DECR` 사이에 다른 스레드가 끼어들 수 있음. 이를 "check-then-act race"라 부른다.

**올바른 패턴 (DECR-first)**:
```
stockAfterDecr = DECR stock  ← 원자 감소
if (stockAfterDecr < 0) {    ← 내가 음수로 만든 거면
    INCR stock               ← 내가 복구
    break
}
```
여러 스레드가 동시에 DECR해도 **서로 다른 결과**를 받는다 (2→1→0→-1). 음수가 된 스레드만 복구한다.

`QueueAutoProcessService.collectWindow` (86–111줄) 핵심:
```java
Long stockAfterDecr = cachePort.decrement(stockKey);
if (stockAfterDecr == null || stockAfterDecr < 0) {
    if (stockAfterDecr != null) cachePort.increment(stockKey);  // ← 복구
    break;
}
String userIdStr = cachePort.popMinFromZSet(queueKey);
if (userIdStr == null) {
    cachePort.increment(stockKey);                              // ← 복구
    break;
}
```

**중요**: DECR이 성공했다고 끝이 아님. **ZPOPMIN이 null이면 stock을 되돌려야 함** (큐가 비었으므로 팔 대상이 없음).

---

## 3. 5개 시나리오 — 타임라인

### 🔥 시나리오 A — 동시 DECR (stock=1, 대기 2명 A·B)

**질문**: 둘 중 누가 티켓을 얻는가? 얻지 못한 쪽은?

**타임라인**:
```
[초기] stock=1, queue=[A(t=0), B(t=1)]

t=0  Thread1: DECR stock → 0  ✅ 성공
     Thread2: DECR stock → -1 ❌ 음수

t=1  Thread2: INCR stock → 0  (복구)
     Thread2: break  (for loop)

t=2  Thread1: ZPOPMIN queue → "A"
     Thread1: INCR paying → 1
     Thread1: tasks.add(A)

[collectWindow 종료]
Thread1은 [A]만 processWindowParallel로 병렬 실행.
B는 queue에 그대로 남아 다음 drain을 기다림.
```

**코드 보호 위치**:
- `QueueAutoProcessService.java:92-96` — `stockAfterDecr < 0 → INCR + break`
- DECR 원자성 덕에 두 스레드가 서로 다른 값(0, -1)을 봄.

---

### 🔥 시나리오 B — DECR 성공 / ZPOPMIN null

**질문**: stock DECR이 성공했는데 ZPOPMIN이 null을 리턴하는 상황은 언제? 어떻게 복구?

**타임라인**:
```
[초기] stock=2, queue=[A]  ← 대기자는 1명인데 stock은 2

t=0  Thread1 (drain A): DECR stock → 1   ✅
                        ZPOPMIN queue → "A"   ✅
                        tasks.add(A)

t=1  Thread1: DECR stock → 0   ✅ (window=min(stock,size)였지만 stock 변동 가능)
              ZPOPMIN queue → null   ❌ 큐 비었음
              → INCR stock → 1   (복구)
              → break

[collectWindow 종료]
tasks=[A]만 처리. stock=1 남음. 나중에 다른 드레인 트리거로 재진입.
checkTermination은 stock>0, queue=0이므로 "대기 중" 판정 → 종료 안 함.
```

**또 다른 케이스** — 병렬 컨슈머 race:
```
[초기] stock=1, queue=[A]
Consumer1: DECR stock → 0
Consumer2: DECR stock → -1 → INCR → 0, break
Consumer1: ZPOPMIN → "A"   ✅

변형:
Consumer1: DECR stock → 0
Consumer2가 동시에 ZPOPMIN queue → "A"  (다른 코드 경로 — QueueService에서?)
Consumer1: ZPOPMIN → null   ❌
→ INCR stock → 1
```

**중요 가드**: `collectWindow`가 **단일 컨슈머 스레드에서 순차 실행**되므로 DECR-then-ZPOPMIN 페어가 교차하지 않는다. Kafka key=scheduleId 파티셔닝으로 **같은 scheduleId는 같은 컨슈머 스레드** → window 수집 자체는 race 안 생김. Race는 "DECR 성공 후 ZPOPMIN 사이에 다른 주체가 ZPOPMIN"인 경우인데, 이 서비스에서 큐에서 꺼내는 경로는 `collectWindow` 하나뿐이므로 실제로는 거의 안 생김.

**코드 보호 위치**: `QueueAutoProcessService.java:97-101` — `userIdStr == null → INCR stock + break`

---

### 🔥 시나리오 C — HTTP 성공 / DB 롤백 (CookieCompensationHelper 동작)

**질문**: `QueuePurchaseProcessor.tryPurchase`에서 `UserPort.deductTicketFee` 성공 후 DB 커넥션 유실로 커밋 실패. 어떻게 보상되는가?

**타임라인** — `tryPurchase` 내부 (`QueuePurchaseProcessor.java:42-67`):
```
t=0  Thread: scheduleRepository.findById()     (DB SELECT)
t=5  Thread: ticket.createReserved(...)
t=6  Thread: ticketRepository.save(ticket)     (DB INSERT, ID 확보)
t=10 Thread: userPort.deductTicketFee(...)     (HTTP POST — 외부 user-service)
       └─ ★ HTTP 성공: 쿠키 차감됨 (t=15)

t=16 Thread: response.flag() == true
     Thread: CookieCompensationHelper.registerRollbackRefund(...)
       └─ TransactionSynchronization 등록 (afterCompletion 콜백)

t=17 Thread: ticket.pay()  → RESERVED → CONFIRMED
     Thread: eventPublisher.publishEvent(TicketPaidEvent)

t=18 @Transactional 커밋 시도
       └─ ❌ DB 커넥션 유실 (네트워크 장애 등)
       └─ 트랜잭션 롤백 → ticket INSERT 무효, event 발행 안 됨

t=19 TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)
       └─ userPort.refundCookie(...) 호출  ← ★ 쿠키 환불 HTTP 성공
       └─ 쿠키 차감 보상 완료
```

**최종 상태**: 티켓 없음, 쿠키 원상 복구, Kafka 미발행 → **완전 일관**.

**리스크 포인트**: `afterCompletion` 안에서 refundCookie **HTTP 호출도 실패**한다면?
```
t=20 userPort.refundCookie → 예외
     log.error("DB 롤백 쿠키 보상 실패 - ticketId=.., 수동 처리 필요", e)
```
→ **수동 개입 필요** (현재 자동 재시도 워커 없음). 경보·모니터링으로 보완.

**코드 위치**: `CookieCompensationHelper.java:21-36`

---

### 🔥 시나리오 D — 환불 직후 큐 드레인 순서

**질문**: 사용자 X가 환불 → TicketEventListener.handleTicketRefunded가 stock INCR + `queue.drain` 발행. 그런데 드레인 컨슈머가 X의 환불 커밋 **이전에** 깨면?

**답**: **그런 일은 일어나지 않는다.** `handleTicketRefunded`는 `@TransactionalEventListener(phase = AFTER_COMMIT)` → DB 커밋이 확정된 이후에만 fire → 그 안에서 stock INCR도, Kafka publish도 일어남. Kafka 컨슈머가 메시지를 받는 시점은 이미 커밋 이후.

**타임라인**:
```
t=0  RefundService.refund() 트랜잭션 시작
     ticketRepository.delete(ticket)           (DB DELETE, 트랜잭션 내부)
     eventPublisher.publishEvent(TicketRefundedEvent)
     @Transactional 끝 → 커밋 시작
t=10 커밋 성공 ✅
t=11 @TransactionalEventListener(AFTER_COMMIT) 트리거:
       handleTicketRefunded:
         try { userPort.refundCookie() } catch { log }
         try { cachePort.increment(STOCK)           ← stock 복구
               eventPublisher.publish(QUEUE_DRAIN)  ← Kafka 발행 } catch { log }
         try { eventPublisher.publish(TICKET_REFUNDED) } catch { log }
t=12 Kafka queue.drain 메시지 브로커에 쌓임
t=15 QueueDrainConsumer가 polling → checkAndProcess() 실행
     → drainQueue → collectWindow → DECR stock → ZPOPMIN → tryPurchase
     → 이 시점에 stock은 이미 복구된 상태여서 문제없이 꺼낼 수 있음 ✅
```

**만약 `@TransactionalEventListener`가 아니라 일반 `@EventListener`였다면?**
```
t=4  eventPublisher.publishEvent(TicketRefundedEvent)  ← 트랜잭션 내부
     → @EventListener 즉시 실행
     → stock INCR, Kafka publish 실행
t=7  DB delete 실패 → 롤백
결과: ticket CONFIRMED 그대로, stock은 복구됨 → 대기자가 진입 → 타인에게 배정 
     → 티켓 1장에 대해 **두 명이 CONFIRMED** → 중복 판매 💥
```

**코드 위치**: `TicketEventListener.handleTicketRefunded` (`infrastructure/event/TicketEventListener.java`)

---

### 🔥 시나리오 E — paying 카운터 누수 (JVM 크래시)

**질문**: `processWindowParallel` 병렬 태스크가 OutOfMemory로 JVM째 죽는다면? `finally` 블록의 paying DECR이 실행 보장되는가?

**답**: **보장되지 않는다.** `finally`는 try 블록의 **throw**를 잡을 수 있지만, JVM이 죽으면 스택 자체가 날아가므로 실행 안 됨.

**타임라인**:
```
[초기] stock=0, queue=[], paying=3  (구매 중 3명)

t=0  Thread-queueExecutor-1: tryPurchase(A) 실행 중
     Thread-queueExecutor-2: tryPurchase(B) 실행 중
     Thread-queueExecutor-3: tryPurchase(C) 실행 중

t=10 OOM 발생 → JVM 종료
     ❌ finally { cachePort.decrement(PAYING) } 실행 안 됨

[JVM 재시작 후]
Redis: stock=0, paying=3, queue=[...]
       ^ stale: 실제로 구매 중인 사람 없음

사용자 D가 QueueService.enter() 시도:
  stock=0 → paying>0 → "결제 진행 중인 사람 있음" 판정 → 대기열 진입 허용
  → 실제로는 아무도 구매 안 함 → D는 영영 드레인되지 않음
```

**자동 복구 수단**:
- **Redis 키 TTL**: paying 키는 `startTime - 10min`까지 유효 → 티켓팅 끝나면 자동 소멸.
- **재초기화**: TicketingStartService가 다음 스케줄 시작 시 `paying=0`으로 seed (TicketingStartedEvent 핸들러에서 `setCounter(PAYING, 0, ttl)`).

**수동 복구가 필요한 창**: 티켓팅 진행 중 JVM 크래시 발생 → 다음 스케줄 시작까지는 stale 상태. 현재 방어는 **모니터링 + 수동 리셋**.

**코드 위치**: `QueueAutoProcessService.java:128-130` — `finally { cachePort.decrement(payingKey); }`

---

## 4. 보너스 — terminateQueue의 중복 발행 방지

`queue.drain`은 concurrency=4로 돌므로 같은 scheduleId가 여러 컨슈머 스레드에서 처리될 수 있다 (Kafka key 파티셔닝이 보장하는 건 "순차성"뿐, 컨슈머 여러 개 중 하나에 몰리는 것). 여러 스레드가 종료 조건을 동시에 만족시키면 **중복 Kafka 발행** 위험.

```java
private void terminateQueue(Long scheduleId) {
    boolean deleted = cachePort.delete(RedisKeys.QUEUE + scheduleId);
    if (!deleted) {
        return;  // ← 이미 다른 스레드가 지움 → 중복 발행 차단
    }
    log.info("대기열 종료 - scheduleId={}", scheduleId);
    eventPublisherPort.publish(KafkaTopics.QUEUE_TERMINATED, ...);
}
```

**핵심**: `DEL`의 반환값을 "내가 첫 번째" 판정에 사용. 두 번째 스레드는 false를 받고 return.

**코드 위치**: `QueueAutoProcessService.java:152-161`

---

## 5. 이 Stage의 "설계 교훈" — DECR-first / AFTER_COMMIT / finally-compensate

1. **"Check-then-act"를 "Decrement-then-check"로 뒤집어라.** 원자 감소가 토큰을 나눠주는 bus 역할을 한다. 음수면 양보.
2. **AFTER_COMMIT이 없으면 Redis/HTTP/Kafka는 DB와 따로 논다.** DB 커밋 실패 시 Redis만 변경되면 시스템은 즉시 오버부킹이나 이중 환불로 간다.
3. **try-finally로 "내가 찍은 카운터는 내가 지운다".** 단, JVM 크래시엔 무력 → TTL·재시작 seed로 2차 방어.
4. **`DEL`의 boolean 반환값은 락이 없는 분산환경에서 "singleton 실행"을 구현하는 도구**. SETNX, DEL 기반 중복 방지 패턴은 기억해둘 것.
5. **"실패는 피할 수 없다. 대신 실패의 영향 범위를 좁힌다."** 독립 try-catch, AFTER_COMMIT, 보상 훅 — 세 계층 모두 "복구 비용이 가장 낮은 쪽으로 실패를 몰아넣는" 도구.

---

## ★ 핵심 질문 (자기 검증)

1. ★ 시나리오 A: `stock=3, 동시 진입 4명`. DECR 결과 각각을 시간 순으로 적고, 성공·실패·복구를 설명하라.
2. ★ 시나리오 C: `CookieCompensationHelper.registerRollbackRefund`는 **DB 커밋 성공**시에도 호출되는가? 코드에서 근거를 들어 답하라. (답: STATUS_ROLLED_BACK일 때만)
3. ★ 시나리오 D: `TicketEventListener.handleTicketRefunded`의 3중 try-catch에서 **첫 번째(refundCookie)만 실패**한 경우 시스템 상태는? (티켓 삭제 + 쿠키 환불 실패 + 재고 복구됨 + Kafka 발행됨 → 수동 개입)
4. 시나리오 E: `paying` 카운터 누수가 실제 현상으로 나타나는 **2개 이상의 사용자 체험 문제**를 구체적으로 서술하라.
5. `terminateQueue`에서 DEL을 맨 위에 둔 이유를 제거하고 DEL을 맨 끝으로 옮기면 어떤 중복 문제가?
6. `collectWindow`와 `processWindowParallel`을 **하나의 루프로 합치면** (수집과 HTTP 호출 섞기) 어떤 문제? (힌트: DECR 원자성을 깬다)

---

## 체크리스트

- [ ] DECR-first 패턴을 의사코드 없이 직접 쓸 수 있다
- [ ] 시나리오 A~E 각각 타임라인을 종이에 그려 설명 가능
- [ ] `CookieCompensationHelper`가 **언제 등록되고 언제 실행되는지** 코드 라인 수준으로 안다
- [ ] `@EventListener`로 바꾸면 생기는 구체 버그 최소 2개를 기억한다 (TX-001, TX-002 형)
- [ ] `paying` 카운터의 INCR/DECR 위치 4곳을 가리킬 수 있다 (QueueService, collectWindow INCR, processWindowParallel finally DECR, TicketingStartedEvent seed)
- [ ] `terminateQueue`의 DEL 반환값이 "중복 발행 방지"에 쓰이는 이유를 설명할 수 있다

---

## 원본 참고

- `src/main/java/com/example/ticketservice/application/service/QueueAutoProcessService.java` (168줄) — drainQueue / collectWindow / processWindowParallel / checkTermination / terminateQueue
- `src/main/java/com/example/ticketservice/application/service/QueuePurchaseProcessor.java` (67줄) — save-before-HTTP / setRollbackOnly / CookieCompensationHelper 호출
- `src/main/java/com/example/ticketservice/application/service/CookieCompensationHelper.java` (37줄) — afterCompletion(STATUS_ROLLED_BACK)
- `src/main/java/com/example/ticketservice/infrastructure/event/TicketEventListener.java` — handleTicketRefunded 3중 try-catch
- `docs/reference/flow/04-queue-drain-strategy.md` — V1 순차 vs V2 윈도우 병렬 비교
- `docs/reference/flow/03-queue-flow-and-implementation.md` — 큐 진입·드레인 전체 흐름
- `docs/troubleshooting/02-transaction-safety.md` — TX-001 ~ TX-007 실제 버그 사례 전체

← 이전: [Stage 6 — Spring Batch](stage-06-spring-batch.md)
→ 다음: [Stage 8 — 테스트 & E2E](stage-08-tests-e2e.md)