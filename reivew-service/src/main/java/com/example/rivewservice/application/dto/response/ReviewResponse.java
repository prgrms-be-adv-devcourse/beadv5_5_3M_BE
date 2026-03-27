package com.example.rivewservice.application.dto.response;

import com.example.rivewservice.domain.enums.ReviewStatus;
import com.example.rivewservice.domain.model.Review;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 응답")
public record ReviewResponse(
        @Schema(description = "리뷰 ID", example = "1") Long reviewId,
        @Schema(description = "작성자 닉네임", example = "홍길동") String nickname,
        @Schema(description = "영화 ID", example = "1") Long movieId,
        @Schema(description = "평점", example = "5") Integer rating,
        @Schema(description = "리뷰 내용", example = "재미있었습니다") String comment,
        @Schema(description = "리뷰 상태", example = "WRITTEN") ReviewStatus status,
        @Schema(description = "작성 시각", example = "2026-03-27T10:00:00") LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getUser().getNickname(),
                review.getMovieId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}