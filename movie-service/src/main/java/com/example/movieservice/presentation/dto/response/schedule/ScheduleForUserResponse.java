package com.example.movieservice.presentation.dto.response.schedule;

import java.time.LocalDateTime;

public record ScheduleForUserResponse(
        Long scheduleId,
        LocalDateTime startTime,
        Integer remainingSeats,
        String status
) {
}
