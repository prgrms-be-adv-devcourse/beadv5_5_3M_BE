package com.example.movieservice.presentation.dto.response;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ScheduleForUserResponse(
        Long scheduleId,
        LocalDateTime startTime,
        Integer remainingSeats,
        String status
) {
}
