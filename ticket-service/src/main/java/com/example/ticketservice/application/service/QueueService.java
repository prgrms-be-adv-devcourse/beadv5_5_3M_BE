package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.response.QueueEntryResponse;
import com.example.ticketservice.application.dto.response.QueuePositionResponse;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.usecase.QueueUseCase;
import com.example.ticketservice.common.exception.QueueErrorCode;
import com.example.ticketservice.common.exception.TicketErrorCode;
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

    private static final String QUEUE_KEY_PREFIX     = "queue:schedule:";
    private static final String STOCK_KEY_PREFIX     = "stock:schedule:";
    private static final String PAYING_KEY_PREFIX    = "paying:schedule:";
    private static final String SEATS_KEY_PREFIX     = "seats:schedule:";
    private static final String COOKIE_KEY_PREFIX    = "cookie:schedule:";
    private static final String START_TIME_KEY_PREFIX = "startTime:schedule:";

    private final CachePort cachePort;
    private final QueuePurchaseProcessor purchaseProcessor;

    // @Transactional 없음: DB 조회 없음, 실제 트랜잭션은 QueuePurchaseProcessor가 관리
    @Override
    public QueueEntryResponse enter(UUID userId, Long scheduleId) {
        String stockKey = STOCK_KEY_PREFIX + scheduleId;
        if (!cachePort.exists(stockKey)) {
            throw QueueErrorCode.QUEUE_NOT_OPEN.of(scheduleId);
        }

        String payingKey = PAYING_KEY_PREFIX + scheduleId;
        Long stockAfterDecr = cachePort.decrement(stockKey);

        if (stockAfterDecr != null && stockAfterDecr >= 0) {
            // stock > 0 → 바로 구매 시도
            Long seats = cachePort.getCounter(SEATS_KEY_PREFIX + scheduleId);
            int ticketNum = (int) (seats - stockAfterDecr);
            cachePort.increment(payingKey);
            Optional<TicketResponse> result;
            try {
                result = purchaseProcessor.tryPurchase(scheduleId, userId, ticketNum);
            } catch (Exception e) {
                // 예외 발생 시 stock 복구 (tryPurchase 내부에서 복구되지 않음)
                cachePort.increment(stockKey);
                throw e;
            } finally {
                cachePort.decrement(payingKey);
            }
            if (result.isPresent()) {
                return QueueEntryResponse.purchased(result.get());
            }
            // 쿠키 부족: stock은 tryPurchase 내부에서 복구됨 → 즉시 에러 반환
            Long cookie = cachePort.getCounter(COOKIE_KEY_PREFIX + scheduleId);
            throw TicketErrorCode.INSUFFICIENT_BALANCE.of(cookie != null ? cookie : 0L);
        } else {
            // stock 없음: decrement 복구
            if (stockAfterDecr != null) {
                cachePort.increment(stockKey);
            }
        }

        // stock == 0: 결제 진행 중인 유저 유무 확인 (paying > 0이면 실패 시 stock 복구 가능)
        Long payingCount = cachePort.getCounter(payingKey);
        if (payingCount == null || payingCount <= 0) {
            throw QueueErrorCode.SOLD_OUT.of(scheduleId);
        }

        // 대기열 진입
        String queueKey = QUEUE_KEY_PREFIX + scheduleId;
        String userIdStr = userId.toString();

        if (cachePort.getZSetRank(queueKey, userIdStr) != null) {
            throw QueueErrorCode.ALREADY_IN_QUEUE.of(scheduleId);
        }

        cachePort.addToZSetWithTimestamp(queueKey, userIdStr);

        // 대기열 TTL: 공연 시작 10분 전 (이미 지난 경우 1초 후 만료)
        String startTimeStr = cachePort.get(START_TIME_KEY_PREFIX + scheduleId, String.class).orElse(null);
        LocalDateTime startTime = startTimeStr != null ? LocalDateTime.parse(startTimeStr) : LocalDateTime.now();
        Duration ttl = Duration.between(LocalDateTime.now(), startTime.minusMinutes(10));
        cachePort.expireKey(queueKey, ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl);

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