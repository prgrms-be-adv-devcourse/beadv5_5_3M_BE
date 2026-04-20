package com.example.movieservice.presentation.dto.response.review;

import com.example.movieservice.domain.model.Review;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        Long reviewId,
        UUID userId,
        String nickname,
        String userImageUrl,
        Long movieId,
        Integer rating,
        String comment,
        Review.ReviewStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse of(Review review, String nickname, String userImageUrl) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getUserId(),
                nickname,
                userImageUrl,
                review.getMovieId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}