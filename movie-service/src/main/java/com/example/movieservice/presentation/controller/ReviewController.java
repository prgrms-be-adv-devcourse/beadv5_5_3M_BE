package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ReviewUseCase;
import com.example.movieservice.presentation.dto.request.review.UpdateReviewRequest;
import com.example.movieservice.presentation.dto.request.review.WriteReviewRequest;
import com.example.movieservice.presentation.dto.response.review.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Review", description = "리뷰 API")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewUseCase reviewUseCase;

    @Operation(summary = "리뷰 작성", description = "티켓 구매 이력이 있는 사용자만 리뷰를 작성할 수 있습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ReviewResponse> write(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid WriteReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewUseCase.write(UUID.fromString(userId), request));
    }

    @Operation(summary = "리뷰 수정")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> update(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long reviewId,
            @RequestBody @Valid UpdateReviewRequest request) {
        return ResponseEntity.ok(reviewUseCase.update(UUID.fromString(userId), reviewId, request));
    }

    @Operation(summary = "리뷰 삭제")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long reviewId) {
        reviewUseCase.delete(UUID.fromString(userId), reviewId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리뷰 단건 조회")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getById(
            @PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewUseCase.getById(reviewId));
    }

    @Operation(summary = "영화별 리뷰 목록")
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getByMovie(
            @Parameter(description = "영화 ID", required = true)
            @RequestParam Long movieId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewUseCase.getByMovie(movieId, pageable));
    }

    @Operation(summary = "내 리뷰 목록")
    @GetMapping("/me")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewUseCase.getMyReviews(UUID.fromString(userId), pageable));
    }
}