package com.example.rivewservice.application.dto.response;

import com.example.rivewservice.domain.enums.ReviewStatus;
import com.example.rivewservice.domain.model.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        String nickname,
        Long movieId,
        Integer rating,
        String comment,
        ReviewStatus status,
        LocalDateTime createdAt
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
