package com.example.movieservice.presentation.dto.response.movie;

import com.example.movieservice.presentation.dto.response.review.ReviewSummaryResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;

import java.util.List;
import java.util.UUID;

public record DetailForUserResponse(
        UUID creatorId,
        String nickname, // 크리에이터 이름
        String title,
        String description,
        List<Long> categoryIds,
        Integer runningTime,
        Float averageRating,
        Integer cookie,
//        String imageUrl,
        List<ScheduleForUserResponse> schedules,
        List<ReviewSummaryResponse> reviews
) {
}
