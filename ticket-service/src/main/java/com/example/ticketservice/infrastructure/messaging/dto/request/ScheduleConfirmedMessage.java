package com.example.ticketservice.infrastructure.messaging.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

//topic: movie.schedule.confirmed
public record ScheduleConfirmedMessage(
        Long scheduleId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String title,
        Integer cookie,
        UUID creatorId,
        Long movieId,
        String imageUrl,
        Integer seats
) {
}
