package com.example.ticketservice.infrastructure.event;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.event.ScheduleInitializedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.SchedulerPort;
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
        // 영상 시작 10분 전: 티켓팅 마감 + 리뷰권한 발행. streaming LOBBY_LEAD(10m)와 동일 시점.
        LocalDateTime ticketingCloseTime = event.startTime().minusMinutes(10);
        LocalDateTime cartCloseTime = event.ticketingTime().minusHours(24);

        schedulerPort.scheduleCartCloseJob(event.scheduleId(), cartCloseTime);
        schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
        schedulerPort.scheduleTicketingCloseJob(event.scheduleId(), ticketingCloseTime);
        schedulerPort.scheduleReviewAuthJob(event.scheduleId(), ticketingCloseTime);
        schedulerPort.scheduleStreamingStartJob(event.scheduleId(), event.startTime());
        schedulerPort.scheduleStreamingFinishJob(event.scheduleId(), event.endTime());
        log.info("Quartz Job 등록 완료 - scheduleId={}, cartCloseTime={}, ticketingTime={}, ticketingCloseTime={}, startTime={}, endTime={}",
                event.scheduleId(), cartCloseTime, event.ticketingTime(), ticketingCloseTime,
                event.startTime(), event.endTime());

        // cart:count TTL은 CartClose 시점까지(=ticketingTime - 24h) 유효
        Duration ttl = Duration.between(LocalDateTime.now(), cartCloseTime);
        String cartCountKey = RedisKeys.CART_COUNT + event.scheduleId();
        if (!ttl.isNegative() && !ttl.isZero()) {
            cachePort.setCounter(cartCountKey, 0, ttl);
            log.debug("장바구니 수요 카운터 초기화 - scheduleId={}, ttl={}s", event.scheduleId(), ttl.getSeconds());
        } else {
            // Redis는 영속되므로 동일 scheduleId가 과거에 쓰였다면 잔존값이 addToCart INCR에 누적됨.
            cachePort.delete(cartCountKey);
        }
    }
}