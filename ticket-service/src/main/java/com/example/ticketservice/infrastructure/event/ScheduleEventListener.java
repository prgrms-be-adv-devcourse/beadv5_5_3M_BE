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
        // ReviewAuth는 streaming LobbyOpen과 동시 발행해야 Entitlement가 WS CONNECT 전에 도달.
        // 관측용: startTime - 6m (streaming LOBBY_LEAD와 동기화). 운영 복원 시 minusMinutes(10).
        LocalDateTime reviewAuthTime = event.startTime().minusMinutes(6);
        // 관측용: ticketingTime - 3m. 운영 복원 시 minusHours(24).
        schedulerPort.scheduleCartCloseJob(event.scheduleId(), event.ticketingTime().minusMinutes(3));
        schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
        schedulerPort.scheduleReviewAuthJob(event.scheduleId(), reviewAuthTime);
        schedulerPort.scheduleStreamingStartJob(event.scheduleId(), event.startTime());
        schedulerPort.scheduleStreamingFinishJob(event.scheduleId(), event.endTime());
        log.info("Quartz Job 등록 완료 - scheduleId={}, ticketingTime={}, reviewAuthTime={}, startTime={}, endTime={}",
                event.scheduleId(), event.ticketingTime(), reviewAuthTime, event.startTime(), event.endTime());

        // cart:count TTL도 CartClose와 동일 오프셋. 운영 복원 시 minusHours(24).
        Duration ttl = Duration.between(LocalDateTime.now(), event.ticketingTime().minusMinutes(3));
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