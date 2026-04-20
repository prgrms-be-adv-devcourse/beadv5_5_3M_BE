package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.review.UpdateReviewRequest;
import com.example.movieservice.presentation.dto.request.review.WriteReviewRequest;
import com.example.movieservice.presentation.dto.response.review.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewUseCase {
    ReviewResponse write(UUID userId, WriteReviewRequest request);
    ReviewResponse update(UUID userId, Long reviewId, UpdateReviewRequest request);
    void delete(UUID userId, Long reviewId);
    ReviewResponse getById(Long reviewId);
    Page<ReviewResponse> getByMovie(Long movieId, Pageable pageable);
    Page<ReviewResponse> getMyReviews(UUID userId, Pageable pageable);
}