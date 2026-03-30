package com.example.movieservice.presentation.dto.response.movie;

import com.example.movieservice.presentation.dto.response.review.ReviewSummaryResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "영화 상세 조회 응답 (사용자용)")
public record DetailForUserResponse(
        @Schema(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID creatorId,

        @Schema(description = "크리에이터 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "영화 설명", example = "우주를 배경으로 한 SF 영화입니다.")
        String description,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds,

        @Schema(description = "상영 시간 (초 단위)", example = "9720")
        Integer runningTime,

        @Schema(description = "평균 평점", example = "4.5")
        Float averageRating,

        @Schema(description = "총 쿠키 수 (기본 + 추가)", example = "15")
        Integer cookie,
//        String imageUrl,
        @Schema(description = "상영 일정 목록")
        List<ScheduleForUserResponse> schedules,

        @Schema(description = "리뷰 목록")
        List<ReviewSummaryResponse> reviews
) {
}
