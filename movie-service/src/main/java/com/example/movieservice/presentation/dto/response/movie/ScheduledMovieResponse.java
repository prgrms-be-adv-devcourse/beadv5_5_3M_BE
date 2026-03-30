package com.example.movieservice.presentation.dto.response.movie;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScheduledMovieResponse(
        Long movieId,
        UUID creatorId,
        String nickname, // 크리에이터 이름
        String title,
        LocalDateTime startTime,
        List<Long> categoryIds
        // todo : 나중에 포스터 이미지 추가
) {
}
