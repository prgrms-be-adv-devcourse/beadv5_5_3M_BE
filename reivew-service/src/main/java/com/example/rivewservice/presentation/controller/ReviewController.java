package com.example.rivewservice.presentation.controller;

import com.example.rivewservice.application.dto.request.UpdateReviewRequest;
import com.example.rivewservice.application.dto.request.WriteReviewRequest;
import com.example.rivewservice.application.dto.response.ReviewResponse;
import com.example.rivewservice.application.usecase.ReviewUseCase;
import com.example.rivewservice.common.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Review", description = "영화 리뷰 CRUD API")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewUseCase reviewUseCase;

    @Operation(summary = "리뷰 작성", description = "영화에 대한 리뷰를 작성합니다. 리뷰 권한(AUTHORIZED 상태)이 있어야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "리뷰 작성 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "리뷰 권한 없음")
    })
    @PostMapping
    public ResponseEntity<ReviewResponse> writeReview(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid WriteReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewUseCase.writeReview(userId, request));
    }

    @Operation(summary = "리뷰 수정", description = "작성한 리뷰를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리뷰 수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long reviewId,
            @RequestBody @Valid UpdateReviewRequest request) {
        return ResponseEntity.ok(reviewUseCase.updateReview(userId, reviewId, request));
    }

    @Operation(summary = "리뷰 삭제", description = "리뷰를 삭제합니다. 상태가 AUTHORIZED로 롤백되어 재작성이 가능해집니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "리뷰 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long reviewId) {
        reviewUseCase.deleteReview(userId, reviewId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리뷰 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewUseCase.getReview(reviewId));
    }

    @Operation(summary = "영화별 리뷰 목록 조회", description = "movieId로 리뷰 목록을 페이지네이션하여 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<PageResult<ReviewResponse>> getReviewsByMovie(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "영화 ID", example = "1", required = true) @RequestParam Long movieId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewUseCase.getReviewsByMovie(
                movieId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Operation(summary = "내 리뷰 목록 조회", description = "X-User-Id 헤더의 사용자가 작성한 리뷰를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me")
    public ResponseEntity<PageResult<ReviewResponse>> getMyReviews(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewUseCase.getMyReviews(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}