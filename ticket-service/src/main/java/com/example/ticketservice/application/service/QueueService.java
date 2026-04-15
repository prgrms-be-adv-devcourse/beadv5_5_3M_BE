package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.response.QueueEntryResponse;
import com.example.ticketservice.application.dto.response.QueuePositionResponse;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.usecase.QueueUseCase;
import com.example.ticketservice.common.exception.QueueErrorCode;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService implements QueueUseCase {

    private static final String QUEUE_KEY_PREFIX = "queue:schedule:";
    private static final String STOCK_KEY_PREFIX = "stock:schedule:";

    private final ScheduleRepository scheduleRepository;
    private final TicketRepository ticketRepository;
    private final CachePort cachePort;
    private final QueuePurchaseProcessor purchaseProcessor;

    // @Transactional 없음: 내부 DB 쿼리는 각자 연결, 실제 트랜잭션은 QueuePurchaseProcessor가 관리
    @Override
    public QueueEntryResponse enter(UUID userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        if (schedule.getStatus() != ScheduleStatus.TICKETING) {
            throw QueueErrorCode.QUEUE_NOT_OPEN.of(scheduleId);
        }

        String stockKey = STOCK_KEY_PREFIX + scheduleId;
        Long stockAfterDecr = cachePort.decrement(stockKey);

        if (stockAfterDecr != null && stockAfterDecr >= 0) {
            // stock > 0 → 바로 구매 시도
            int ticketNum = (int) (schedule.getSeats() - stockAfterDecr);
            Optional<TicketResponse> result = purchaseProcessor.tryPurchase(scheduleId, userId, ticketNum);
            if (result.isPresent()) {
                return QueueEntryResponse.purchased(result.get());
            }
            // 쿠키 부족: stock은 tryPurchase 내부에서 복구됨 → 즉시 에러 반환
            throw TicketErrorCode.INSUFFICIENT_BALANCE.of((long) schedule.getCookie());
        } else {
            // stock 없음: decrement 복구
            if (stockAfterDecr != null) {
                cachePort.increment(stockKey);
            }
        }

        // stock == 0: RESERVED 티켓 유무 확인
        long reservedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.RESERVED);
        if (reservedCount == 0) {
            throw QueueErrorCode.SOLD_OUT.of(scheduleId);
        }

        // 대기열 진입
        String queueKey = QUEUE_KEY_PREFIX + scheduleId;
        String userIdStr = userId.toString();

        if (cachePort.getZSetRank(queueKey, userIdStr) != null) {
            throw QueueErrorCode.ALREADY_IN_QUEUE.of(scheduleId);
        }

        cachePort.addToZSetWithTimestamp(queueKey, userIdStr);

        // 대기열 TTL: 공연 시작 10분 전
        Duration ttl = Duration.between(LocalDateTime.now(), schedule.getStartTime().minusMinutes(10));
        if (!ttl.isNegative() && !ttl.isZero()) {
            cachePort.expireKey(queueKey, ttl);
        }

        Long rank = cachePort.getZSetRank(queueKey, userIdStr);
        long position = rank != null ? rank + 1 : 1;

        log.info("대기열 진입 - scheduleId={}, userId={}, position={}", scheduleId, userId, position);
        return QueueEntryResponse.queued(scheduleId, position);
    }

    @Override
    public QueuePositionResponse getPosition(UUID userId, Long scheduleId) {
        Long rank = cachePort.getZSetRank(QUEUE_KEY_PREFIX + scheduleId, userId.toString());
        long position = rank != null ? rank + 1 : 0;
        return new QueuePositionResponse(scheduleId, position);
    }
}