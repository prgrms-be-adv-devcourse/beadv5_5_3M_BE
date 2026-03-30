package com.example.movieservice.presentation.dto.response.movie;

public record MovieForScheduleResponse(
        Long movieId,
        String title,
        Integer runningTime
) {
}
