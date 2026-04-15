package com.example.ticketservice.application.listener;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.domain.event.CartItemAddedEvent;
import com.example.ticketservice.domain.event.CartItemRemovedEvent;
import com.example.ticketservice.domain.event.ScheduleConfirmedEvent;
import com.example.ticketservice.domain.model.Schedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCacheEventListener {

    private static final String CART_COUNT_KEY_PREFIX = "cart:schedule:";

    private final CachePort cachePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleConfirmed(ScheduleConfirmedEvent event) {
        Schedule schedule = event.schedule();

        // TTL = ticketingTime 하루 전까지
        Duration ttl = Duration.between(LocalDateTime.now(), schedule.getTicketingTime().minusDays(1));
        if (ttl.isNegative() || ttl.isZero()) {
            log.warn("[Cache] TTL이 유효하지 않아 Redis 저장 스킵 - scheduleId={}, ticketingTime={}", schedule.getId(), schedule.getTicketingTime());
            return;
        }

        String countKey = CART_COUNT_KEY_PREFIX + schedule.getId();
        cachePort.set(countKey, 0L, ttl);
        log.info("[Cache] 스케줄 Redis 저장 완료 - scheduleId={}, ttl={}", schedule.getId(), ttl);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCartItemAdded(CartItemAddedEvent event) {
        cachePort.increment(CART_COUNT_KEY_PREFIX + event.scheduleId());
        log.debug("[Cache] 장바구니 카운터 증가 - scheduleId={}", event.scheduleId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCartItemRemoved(CartItemRemovedEvent event) {
        cachePort.decrement(CART_COUNT_KEY_PREFIX + event.scheduleId());
        log.debug("[Cache] 장바구니 카운터 감소 - scheduleId={}", event.scheduleId());
    }
}
