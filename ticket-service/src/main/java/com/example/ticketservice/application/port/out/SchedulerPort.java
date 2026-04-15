package com.example.ticketservice.application.port.out;

import java.time.LocalDateTime;

public interface SchedulerPort {

    void scheduleCartCloseJob(Long scheduleId, LocalDateTime triggerTime);

    void scheduleTicketingStartJob(Long scheduleId, LocalDateTime triggerTime);

    void scheduleReviewAuthJob(Long scheduleId, LocalDateTime triggerTime);

    void cancelScheduledJobs(Long scheduleId);
}