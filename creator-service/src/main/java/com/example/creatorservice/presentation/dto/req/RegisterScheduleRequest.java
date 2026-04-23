package com.example.creatorservice.presentation.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record RegisterScheduleRequest(
        @NotNull @Positive Long movieId,
        @NotEmpty @Valid List<ScheduleSlot> slots
) {
    public record ScheduleSlot(
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime ticketingTime
    ) {}
}