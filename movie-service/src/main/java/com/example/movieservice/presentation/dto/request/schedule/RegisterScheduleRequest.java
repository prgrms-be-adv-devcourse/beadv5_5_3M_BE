package com.example.movieservice.presentation.dto.request.schedule;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RegisterScheduleRequest(
        @NotNull Long movieId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime
) {
}
