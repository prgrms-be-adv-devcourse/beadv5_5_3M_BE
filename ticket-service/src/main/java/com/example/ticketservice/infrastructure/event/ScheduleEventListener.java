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
        // ReviewAuth 는 streaming-service LOBBY_OPEN (startTime - 10m) 시점에 맞춰 발행.
        // Entitlement 사본이 대기실 개방 전에 도착해야 WebSocket CONNECT 시 권한 검증이 통과됨.
        LocalDateTime reviewAuthTime = event.startTime().minusMinutes(1);//10
        schedulerPort.scheduleCartCloseJob(event.scheduleId(), event.ticketingTime().minusMinutes(3));//minusHours(24)
        schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
        schedulerPort.scheduleReviewAuthJob(event.scheduleId(), reviewAuthTime);
        schedulerPort.scheduleStreamingStartJob(event.scheduleId(), event.startTime());
        schedulerPort.scheduleStreamingFinishJob(event.scheduleId(), event.endTime());
        log.info("Quartz Job 등록 완료 - scheduleId={}, ticketingTime={}, reviewAuthTime={}, startTime={}, endTime={}",
                event.scheduleId(), event.ticketingTime(), reviewAuthTime, event.startTime(), event.endTime());

        Duration ttl = Duration.between(LocalDateTime.now(), event.ticketingTime().minusMinutes(3));//minusHours(24)
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