package com.example.movieservice.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record DetailForUserResponse(
        UUID creatorId,
        String title,
        String description,
        List<Long> categoryIds,
        Integer runningTime,
        Float averageRating,
        Integer cookie
        // 여기에 이미지, 리뷰들, 상영 일정을 추가해야 함
) {
}
