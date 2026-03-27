package com.example.rivewservice.application.service;

import com.example.rivewservice.application.dto.request.UpdateReviewRequest;
import com.example.rivewservice.application.dto.request.WriteReviewRequest;
import com.example.rivewservice.application.dto.response.ReviewResponse;
import com.example.rivewservice.application.port.out.ReviewEventPublisherPort;
import com.example.rivewservice.application.usecase.ReviewUseCase;
import com.example.rivewservice.common.exception.ReviewErrorCode;
import com.example.rivewservice.common.exception.UserErrorCode;
import com.example.rivewservice.common.model.PageResult;
import com.example.rivewservice.domain.model.Review;
import com.example.rivewservice.domain.model.User;
import com.example.rivewservice.domain.repository.ReviewRepository;
import com.example.rivewservice.domain.repository.UserRepository;
import com.example.rivewservice.infrastructure.messaging.dto.event.ReviewDeletedMessage;
import com.example.rivewservice.infrastructure.messaging.dto.event.ReviewUpdatedMessage;
import com.example.rivewservice.infrastructure.messaging.dto.event.ReviewWrittenMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private static final String REVIEW_WRITTEN_TOPIC = "review.written";
    private static final String REVIEW_UPDATED_TOPIC = "review.updated";
    private static final String REVIEW_DELETED_TOPIC = "review.deleted";

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ReviewEventPublisherPort eventPublisherPort;

    @Override
    public ReviewResponse writeReview(UUID userId, WriteReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserErrorCode.USER_NOT_FOUND.of(userId));

        if (user.getFlag()) {
            throw UserErrorCode.USER_DELETED.of(userId);
        }

        if (reviewRepository.existsByUserUserIdAndMovieIdAndFlag(userId, request.movieId(), false)) {
            throw ReviewErrorCode.REVIEW_ALREADY_WRITTEN.of(request.movieId());
        }

        Review review = reviewRepository.findFirstByUserUserIdAndMovieIdAndFlag(userId, request.movieId(), true)
                .orElseThrow(() -> ReviewErrorCode.REVIEW_NOT_AUTHORIZED.of(request.movieId()));

        review.write(request.comment(), request.rating());
        Review saved = reviewRepository.save(review);

        eventPublisherPort.publish(
                REVIEW_WRITTEN_TOPIC,
                saved.getReviewId().toString(),
                new ReviewWrittenMessage(
                        saved.getReviewId(),
                        saved.getRating(),
                        saved.getComment(),
                        saved.getMovieId()
                )
        );

        return ReviewResponse.from(saved);
    }


    @Override
    public ReviewResponse updateReview(UUID userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ReviewErrorCode.REVIEW_NOT_FOUND.of(reviewId));

        if (!review.getUser().getUserId().equals(userId)) {
            throw ReviewErrorCode.REVIEW_NOT_AUTHORIZED.of(reviewId);
        }

        if (review.getFlag()) {
            throw ReviewErrorCode.REVIEW_NOT_AUTHORIZED.of(reviewId);
        }

        review.update(request.rating(), request.comment());
        Review saved = reviewRepository.save(review);

        eventPublisherPort.publish(
                REVIEW_UPDATED_TOPIC,
                saved.getReviewId().toString(),
                new ReviewUpdatedMessage(
                        saved.getReviewId(),
                        saved.getRating(),
                        saved.getComment()
                )
        );

        return ReviewResponse.from(saved);
    }


    @Override
    public void deleteReview(UUID userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ReviewErrorCode.REVIEW_NOT_FOUND.of(reviewId));

        if (!review.getUser().getUserId().equals(userId)) {
            throw ReviewErrorCode.REVIEW_NOT_AUTHORIZED.of(reviewId);
        }

        if (review.getFlag()) {
            throw ReviewErrorCode.REVIEW_NOT_AUTHORIZED.of(reviewId);
        }

        review.delete();
        reviewRepository.save(review);

        eventPublisherPort.publish(
                REVIEW_DELETED_TOPIC,
                reviewId.toString(),
                new ReviewDeletedMessage(reviewId)
        );
    }


    @Transactional(readOnly = true)
    @Override
    public ReviewResponse getReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ReviewErrorCode.REVIEW_NOT_FOUND.of(reviewId));
        return ReviewResponse.from(review);
    }


    @Transactional(readOnly = true)
    @Override
    public PageResult<ReviewResponse> getReviewsByMovie(Long movieId, Pageable pageable) {
        return PageResult.from(
                reviewRepository.findAllByMovieIdAndFlagFalse(movieId, pageable).map(ReviewResponse::from)
        );
    }


    @Transactional(readOnly = true)
    @Override
    public PageResult<ReviewResponse> getMyReviews(UUID userId, Pageable pageable) {
        return PageResult.from(
                reviewRepository.findAllByUserIdAndFlagFalse(userId, pageable).map(ReviewResponse::from)
        );
    }
}