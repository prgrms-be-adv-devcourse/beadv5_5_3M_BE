package com.example.movieservice.presentation.dto.request.schedule;

import jakarta.validation.constraints.NotNull;

public record UpdateConfirmRequest(
        @NotNull Long scheduleId
) {
}
