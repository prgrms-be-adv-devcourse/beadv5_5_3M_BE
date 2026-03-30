package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "상영 예정 영화 응답")
public record ScheduledMovieResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID creatorId,

        @Schema(description = "크리에이터 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "가장 빠른 상영 시작 시간", example = "2026-04-01T14:00:00")
        LocalDateTime startTime,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds
        // todo : 나중에 포스터 이미지 추가
) {
}
