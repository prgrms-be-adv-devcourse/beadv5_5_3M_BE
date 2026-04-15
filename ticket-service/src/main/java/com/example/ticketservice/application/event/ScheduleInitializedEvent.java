package com.example.ticketservice.application.event;

import java.time.LocalDateTime;

public record ScheduleInitializedEvent(
        Long scheduleId,
        LocalDateTime ticketingTime,
        LocalDateTime startTime
) {}