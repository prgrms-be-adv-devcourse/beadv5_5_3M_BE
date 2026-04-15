package com.example.ticketservice.infrastructure.event;

import com.example.ticketservice.application.event.ScheduleInitializedEvent;
import com.example.ticketservice.application.port.out.SchedulerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventListener {

    private final SchedulerPort schedulerPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleScheduleInitialized(ScheduleInitializedEvent event) {
        schedulerPort.scheduleCartCloseJob(event.scheduleId(), event.ticketingTime().minusHours(24));
        schedulerPort.scheduleTicketingStartJob(event.scheduleId(), event.ticketingTime());
        schedulerPort.scheduleReviewAuthJob(event.scheduleId(), event.startTime());
        log.info("Quartz Job 등록 완료 - scheduleId={}, ticketingTime={}, startTime={}",
                event.scheduleId(), event.ticketingTime(), event.startTime());
    }
}