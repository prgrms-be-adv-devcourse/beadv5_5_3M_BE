package com.example.movieservice.presentation.dto.response;

public record MovieForScheduleResponse(
        Long movieId,
        String title,
        Integer runningTime
) {
}
