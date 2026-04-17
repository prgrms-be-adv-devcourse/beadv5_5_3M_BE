package com.example.ticketservice.infrastructure.event;

import com.example.ticketservice.application.event.ScheduleInitializedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.SchedulerPort;
import com.example.ticketservice.application.service.CartService;
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
public class ScheduleEventListener {


    private final SchedulerPort schedulerPort;
    private final CachePort cachePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleScheduleInitialized(ScheduleInitializedEvent event) {
        schedulerPort.scheduleCartCloseJob(event.scheduleId(), event.ticketingTime().minusHours(24));
        schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
        schedulerPort.scheduleReviewAuthJob(event.scheduleId(), event.startTime());
        schedulerPort.scheduleStreamingStartJob(event.scheduleId(), event.startTime());
        schedulerPort.scheduleStreamingFinishJob(event.scheduleId(), event.endTime());
        log.info("Quartz Job 등록 완료 - scheduleId={}, ticketingTime={}, startTime={}, endTime={}",
                event.scheduleId(), event.ticketingTime(), event.startTime(), event.endTime());

        Duration ttl = Duration.between(LocalDateTime.now(), event.ticketingTime().minusHours(24));
        if (!ttl.isNegative() && !ttl.isZero()) {
            cachePort.setCounter(CartService.CART_COUNT_KEY_PREFIX + event.scheduleId(), 0, ttl);
            log.debug("장바구니 수요 카운터 초기화 - scheduleId={}, ttl={}s", event.scheduleId(), ttl.getSeconds());
        }
    }
}