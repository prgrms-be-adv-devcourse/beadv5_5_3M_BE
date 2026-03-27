package com.example.movieservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateConfirmRequest(
        @NotNull Long scheduleId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime
) {
}
