package com.example.movieservice.presentation.dto.response;

import java.time.LocalDateTime;

public record ScheduleForCreatorResponse(
        Long scheduleId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer totalSeats,
        Integer remainingSeats
        // todo : 나중에 이미지도 추가해야 함
) {
}
