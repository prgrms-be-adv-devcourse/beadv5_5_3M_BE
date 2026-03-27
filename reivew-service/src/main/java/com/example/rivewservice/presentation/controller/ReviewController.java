package com.example.rivewservice.presentation.controller;

import com.example.rivewservice.application.dto.request.UpdateReviewRequest;
import com.example.rivewservice.application.dto.request.WriteReviewRequest;
import com.example.rivewservice.application.dto.response.ReviewResponse;
import com.example.rivewservice.application.usecase.ReviewUseCase;
import com.example.rivewservice.common.model.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewUseCase reviewUseCase;

    @PostMapping
    public ResponseEntity<ReviewResponse> writeReview(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid WriteReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewUseCase.writeReview(userId, request));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long reviewId,
            @RequestBody @Valid UpdateReviewRequest request) {
        return ResponseEntity.ok(reviewUseCase.updateReview(userId, reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long reviewId) {
        reviewUseCase.deleteReview(userId, reviewId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewUseCase.getReview(reviewId));
    }

    @GetMapping
    public ResponseEntity<PageResult<ReviewResponse>> getReviewsByMovie(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam Long movieId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewUseCase.getReviewsByMovie(
                movieId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/me")
    public ResponseEntity<PageResult<ReviewResponse>> getMyReviews(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewUseCase.getMyReviews(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}