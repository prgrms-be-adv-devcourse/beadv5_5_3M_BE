package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueTerminatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAutoProcessService {

    static final String STOCK_KEY_PREFIX = "stock:schedule:";
    static final String QUEUE_KEY_PREFIX = "queue:schedule:";
    static final String PAYING_KEY_PREFIX = "paying:schedule:";
    private static final String QUEUE_TERMINATED_TOPIC = "queue.terminated";

    private final CachePort cachePort;
    private final ScheduleRepository scheduleRepository;
    private final QueuePurchaseProcessor purchaseProcessor;
    private final EventPublisherPort eventPublisherPort;

    /**
     * 재고 복구 시 or 결제 완료 후 대기열을 드레인하고 종료 조건을 체크한다.
     * @Async: 호출 스레드(트랜잭션)를 블록하지 않음
     */
    @Async
    public void checkAndProcess(Long scheduleId) {
        Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);
        if (queueSize == null || queueSize == 0) {
            return; // 대기열 비어있음
        }

        drainQueue(scheduleId);
        checkTermination(scheduleId);
    }

    private void drainQueue(Long scheduleId) {
        while (true) {
            Long stock = cachePort.getCounter(STOCK_KEY_PREFIX + scheduleId);
            Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);

            if (stock == null || stock <= 0 || queueSize == null || queueSize == 0) {
                break;
            }

            int window = (int) Math.min(stock, queueSize);
            List<PurchaseTask> tasks = collectWindow(scheduleId, window);

            if (tasks.isEmpty()) {
                break;
            }

            processWindowParallel(scheduleId, tasks);
            // 실패한 유저는 tryPurchase 내에서 stock INCR 복구됨
            // 다음 루프에서 새 window 재계산
        }
    }

    /**
     * window 크기만큼 DECR + ZPOPMIN 순차 수집.
     * DECR-first 방식으로 원자성 및 초과 선점 방지 유지.
     * 수집 성공한 task마다 paying INCR (processWindowParallel에서 finally로 DECR).
     */
    private List<PurchaseTask> collectWindow(Long scheduleId, int window) {
        List<PurchaseTask> tasks = new ArrayList<>(window);
        String stockKey = STOCK_KEY_PREFIX + scheduleId;
        String queueKey = QUEUE_KEY_PREFIX + scheduleId;
        String payingKey = PAYING_KEY_PREFIX + scheduleId;

        for (int i = 0; i < window; i++) {
            Long stockAfterDecr = cachePort.decrement(stockKey);
            if (stockAfterDecr == null || stockAfterDecr < 0) {
                if (stockAfterDecr != null) cachePort.increment(stockKey);
                break;
            }
            String userIdStr = cachePort.popMinFromZSet(queueKey);
            if (userIdStr == null) {
                cachePort.increment(stockKey);
                break;
            }
            try {
                tasks.add(new PurchaseTask(UUID.fromString(userIdStr), computeTicketNum(scheduleId, stockAfterDecr)));
                cachePort.increment(payingKey);
            } catch (IllegalArgumentException e) {
                log.error("대기열 UUID 파싱 실패 - scheduleId={}, value='{}', stock 복구", scheduleId, userIdStr);
                cachePort.increment(stockKey);
            }
        }
        return tasks;
    }

    /**
     * 수집된 tasks를 CompletableFuture로 병렬 실행 후 전체 완료 대기.
     * ForkJoinPool.commonPool() 사용 → @Async 스레드풀과 분리되어 데드락 없음.
     * 각 task는 완료(성공/실패/예외) 후 paying DECR.
     */
    private void processWindowParallel(Long scheduleId, List<PurchaseTask> tasks) {
        String stockKey = STOCK_KEY_PREFIX + scheduleId;
        String payingKey = PAYING_KEY_PREFIX + scheduleId;
        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    try {
                        purchaseProcessor.tryPurchase(scheduleId, task.userId(), task.ticketNum());
                    } catch (Exception e) {
                        log.error("tryPurchase 예외 - scheduleId={}, userId={}, stock 복구", scheduleId, task.userId(), e);
                        cachePort.increment(stockKey);
                    } finally {
                        cachePort.decrement(payingKey);
                    }
                }))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private record PurchaseTask(UUID userId, int ticketNum) {}

    private void checkTermination(Long scheduleId) {
        Long stockLeft = cachePort.getCounter(STOCK_KEY_PREFIX + scheduleId);
        Long payingCount = cachePort.getCounter(PAYING_KEY_PREFIX + scheduleId);
        Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);

        boolean noStock = stockLeft == null || stockLeft <= 0;
        boolean noPaying = payingCount == null || payingCount <= 0;
        boolean queueHasWaiters = queueSize != null && queueSize > 0;

        if (noStock && noPaying && queueHasWaiters) {
            terminateQueue(scheduleId);
        }
    }

    private void terminateQueue(Long scheduleId) {
        // atomic DEL: 첫 번째 스레드만 true 반환 → 중복 Kafka 발행 방지
        boolean deleted = cachePort.delete(QUEUE_KEY_PREFIX + scheduleId);
        if (!deleted) {
            return;
        }
        log.info("대기열 종료 - scheduleId={}", scheduleId);
        eventPublisherPort.publish(QUEUE_TERMINATED_TOPIC, scheduleId.toString(),
                new QueueTerminatedMessage(scheduleId));
    }

    private int computeTicketNum(Long scheduleId, long stockAfterDecr) {
        return scheduleRepository.findById(scheduleId)
                .map(s -> (int) (s.getSeats() - stockAfterDecr))
                .orElse(0);
    }
}