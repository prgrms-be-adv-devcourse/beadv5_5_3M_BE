package com.example.creatorservice.presentation.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmScheduleRequest(
        @NotEmpty List<Long> scheduleIds
) {}