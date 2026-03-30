package com.example.movieservice.presentation.dto.response.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 요약 응답")
public record ReviewSummaryResponse(
        @Schema(description = "리뷰 ID", example = "1")
        Long reviewId,

        @Schema(description = "작성자 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "평점 (1~5)", example = "5")
        Integer rating,

        @Schema(description = "리뷰 내용", example = "정말 재밌었어요!")
        String comment,

        @Schema(description = "마지막 수정 시간", example = "2026-03-30T12:00:00")
        LocalDateTime updatedAt,

        @Schema(description = "리뷰 상태 (ACTIVE / DELETED)", example = "ACTIVE")
        String status
) {
}
