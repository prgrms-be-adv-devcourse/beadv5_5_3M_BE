package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueTerminatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAutoProcessService {

    static final String STOCK_KEY_PREFIX = "stock:schedule:";
    static final String QUEUE_KEY_PREFIX = "queue:schedule:";
    private static final String QUEUE_TERMINATED_TOPIC = "queue.terminated";

    private final CachePort cachePort;
    private final TicketRepository ticketRepository;
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
            Long stockAfterDecr = cachePort.decrement(STOCK_KEY_PREFIX + scheduleId);
            if (stockAfterDecr == null || stockAfterDecr < 0) {
                if (stockAfterDecr != null) {
                    cachePort.increment(STOCK_KEY_PREFIX + scheduleId);
                }
                break; // 재고 없음
            }

            String userIdStr = cachePort.popMinFromZSet(QUEUE_KEY_PREFIX + scheduleId);
            if (userIdStr == null) {
                cachePort.increment(STOCK_KEY_PREFIX + scheduleId);
                break; // 대기열 비어있음
            }

            UUID userId = UUID.fromString(userIdStr);
            int ticketNum = computeTicketNum(scheduleId, stockAfterDecr);

            // empty() 반환이면 stock은 이미 복구된 상태 → 다음 사람 시도 (loop 계속)
            try {
                purchaseProcessor.tryPurchase(scheduleId, userId, ticketNum);
            } catch (Exception e) {
                log.error("tryPurchase 예외 - scheduleId={}, userId={}, stock 복구", scheduleId, userId, e);
                cachePort.increment(STOCK_KEY_PREFIX + scheduleId);
            }
        }
    }

    private void checkTermination(Long scheduleId) {
        Long stockLeft = cachePort.getCounter(STOCK_KEY_PREFIX + scheduleId);
        long reservedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.RESERVED);
        Long queueSize = cachePort.getZSetSize(QUEUE_KEY_PREFIX + scheduleId);

        boolean noStock = stockLeft == null || stockLeft <= 0;
        boolean noReserved = reservedCount == 0;
        boolean queueHasWaiters = queueSize != null && queueSize > 0;

        if (noStock && noReserved && queueHasWaiters) {
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