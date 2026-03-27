package com.example.rivewservice.application.usecase;

import com.example.rivewservice.application.dto.request.UpdateReviewRequest;
import com.example.rivewservice.application.dto.request.WriteReviewRequest;
import com.example.rivewservice.application.dto.response.ReviewResponse;
import com.example.rivewservice.common.model.PageResult;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewUseCase {
    ReviewResponse writeReview(UUID userId, WriteReviewRequest request);
    ReviewResponse updateReview(UUID userId, Long reviewId, UpdateReviewRequest request);
    void deleteReview(UUID userId, Long reviewId);
    ReviewResponse getReview(Long reviewId);
    PageResult<ReviewResponse> getReviewsByMovie(Long movieId, Pageable pageable);
    PageResult<ReviewResponse> getMyReviews(UUID userId, Pageable pageable);
}