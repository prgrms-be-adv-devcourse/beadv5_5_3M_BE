package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ReviewUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.model.ReviewAuthorization;
import com.example.movieservice.domain.model.UserSync;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewAuthorizationRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import com.example.movieservice.domain.repository.UserSyncRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.request.review.UpdateReviewRequest;
import com.example.movieservice.presentation.dto.request.review.WriteReviewRequest;
import com.example.movieservice.presentation.dto.response.review.ReviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private static final String UNKNOWN_USER = "알 수 없음";

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;
    private final UserSyncRepository userSyncRepository;
    private final ReviewAuthorizationRepository reviewAuthorizationRepository;

    @Override
    @Transactional
    public ReviewResponse write(UUID userId, WriteReviewRequest request) {
        ReviewAuthorization authorization = reviewAuthorizationRepository.findByUserIdAndMovieId(userId, request.movieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_AUTHORIZED));

        if (reviewRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            throw new GeneralException(ErrorStatus.REVIEW_ALREADY_EXISTS);
        }

        Movie movie = movieRepository.findByMovieIdForUpdate(request.movieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        UserSync userSync = userSyncRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Review review = Review.builder()
                .userId(userId)
                .nickname(userSync.getNickname())
                .rating(request.rating())
                .comment(request.comment())
                .movieId(request.movieId())
                .ticketId(authorization.getTicketId())
                .scheduleId(authorization.getScheduleId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(Review.ReviewStatus.CREATE)
                .build();

        Review saved;
        try {
            saved = reviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Review] 중복 리뷰 skip (UNIQUE 위반) - userId: {}, movieId: {}", userId, request.movieId());
            throw new GeneralException(ErrorStatus.REVIEW_ALREADY_EXISTS);
        }
        movie.applyReviewCreated(request.rating());

        log.info("[Review] 작성 완료 - reviewId: {}, userId: {}, movieId: {}", saved.getReviewId(), userId, request.movieId());
        return ReviewResponse.of(saved, userSync.getNickname(), userSync.getUrl());
    }

    @Override
    @Transactional
    public ReviewResponse update(UUID userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new GeneralException(ErrorStatus.REVIEW_FORBIDDEN);
        }

        Movie movie = movieRepository.findByMovieIdForUpdate(review.getMovieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        int oldRating = review.getRating();
        review.update(request.rating(), request.comment());
        movie.applyReviewUpdated(oldRating, request.rating());

        UserSync userSync = userSyncRepository.findById(userId).orElse(null);
        String nickname = userSync != null ? userSync.getNickname() : UNKNOWN_USER;
        String url = userSync != null ? userSync.getUrl() : null;

        log.info("[Review] 수정 완료 - reviewId: {}", reviewId);
        return ReviewResponse.of(review, nickname, url);
    }

    @Override
    @Transactional
    public void delete(UUID userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new GeneralException(ErrorStatus.REVIEW_FORBIDDEN);
        }

        Movie movie = movieRepository.findByMovieIdForUpdate(review.getMovieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        reviewRepository.delete(review);
        movie.applyReviewDeleted(review.getRating());

        log.info("[Review] 삭제 완료 - reviewId: {}", reviewId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getById(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_FOUND));
        UserSync userSync = userSyncRepository.findById(review.getUserId()).orElse(null);
        String nickname = userSync != null ? userSync.getNickname() : UNKNOWN_USER;
        String url = userSync != null ? userSync.getUrl() : null;
        return ReviewResponse.of(review, nickname, url);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByMovie(Long movieId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByMovieId(movieId, pageable);

        Set<UUID> userIds = reviews.stream()
                .map(Review::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, UserSync> userSyncMap = userSyncRepository.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(UserSync::getUserId, Function.identity()));

        return reviews.map(review -> {
            UserSync userSync = userSyncMap.get(review.getUserId());
            String nickname = userSync != null ? userSync.getNickname() : UNKNOWN_USER;
            String url = userSync != null ? userSync.getUrl() : null;
            return ReviewResponse.of(review, nickname, url);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(UUID userId, Pageable pageable) {
        UserSync userSync = userSyncRepository.findById(userId).orElse(null);
        String nickname = userSync != null ? userSync.getNickname() : UNKNOWN_USER;
        String url = userSync != null ? userSync.getUrl() : null;
        return reviewRepository.findByUserId(userId, pageable)
                .map(review -> ReviewResponse.of(review, nickname, url));
    }
}