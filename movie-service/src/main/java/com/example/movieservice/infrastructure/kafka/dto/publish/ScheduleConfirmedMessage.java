package com.example.movieservice.infrastructure.kafka.dto.publish;

import java.time.LocalDateTime;
import java.util.UUID;

// topic: movie.schedule.confirmed
// receiver: ticket-service
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
