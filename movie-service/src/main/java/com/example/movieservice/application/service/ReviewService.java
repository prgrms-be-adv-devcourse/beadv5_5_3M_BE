package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ReviewUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewWrittenMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;

    @Override
    @Transactional
    public void handleReviewWritten(ReviewWrittenMessage msg) {
        if (reviewRepository.existsById(msg.reviewId())) {
            log.warn("[Kafka] 중복 메시지 skip - reviewId: {}", msg.reviewId());
            return;
        }

        Optional<Movie> movieOpt = movieRepository.findByMovieId(msg.movieId());
        if (movieOpt.isEmpty()) {
            log.warn("[Kafka] 대상 영화 없음 skip - movieId: {}", msg.movieId());
            return;
        }

        Review review = Review.builder()
                .reviewId(msg.reviewId())
                .userId(msg.userId())
                .nickname(msg.nickname())
                .rating(msg.rating())
                .comment(msg.content())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .movieId(msg.movieId())
                .status(Review.ReviewStatus.CREATE)
                .build();
        reviewRepository.save(review);
        movieOpt.get().applyReviewCreated(msg.rating());
    }

    @Override
    @Transactional
    public void handleReviewUpdated(ReviewUpdatedMessage msg) {
        Optional<Review> reviewOpt = reviewRepository.findById(msg.reviewId());
        if (reviewOpt.isEmpty()) {
            log.warn("[Kafka] 대상 리뷰 없음 skip - reviewId: {}", msg.reviewId());
            return;
        }

        Review review = reviewOpt.get();
        Optional<Movie> movieOpt = movieRepository.findByMovieId(review.getMovieId());
        if (movieOpt.isEmpty()) {
            log.warn("[Kafka] 대상 영화 없음 skip - movieId: {}", review.getMovieId());
            return;
        }

        Integer oldRating = review.getRating();
        review.update(msg.rating(), msg.content());
        movieOpt.get().applyReviewUpdated(oldRating, msg.rating());
    }

    @Override
    @Transactional
    public void handleReviewDeleted(ReviewDeletedMessage msg) {
        Optional<Review> reviewOpt = reviewRepository.findById(msg.reviewId());
        if (reviewOpt.isEmpty()) {
            log.warn("[Kafka] 이미 삭제된 리뷰 skip - reviewId: {}", msg.reviewId());
            return;
        }

        Review review = reviewOpt.get();
        Optional<Movie> movieOpt = movieRepository.findByMovieId(review.getMovieId());

        reviewRepository.delete(review);

        if (movieOpt.isEmpty()) {
            log.warn("[Kafka] 영화 없음, 리뷰만 삭제 처리 - movieId: {}", review.getMovieId());
            return;
        }
        movieOpt.get().applyReviewDeleted(review.getRating());
    }
}
