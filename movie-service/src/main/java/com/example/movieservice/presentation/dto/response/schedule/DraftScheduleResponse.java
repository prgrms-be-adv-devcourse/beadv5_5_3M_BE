package com.example.movieservice.presentation.dto.response.schedule;

import java.time.LocalDateTime;

public record DraftScheduleResponse(
        Long scheduleId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Boolean isConfirmed,
        String status
) {}
