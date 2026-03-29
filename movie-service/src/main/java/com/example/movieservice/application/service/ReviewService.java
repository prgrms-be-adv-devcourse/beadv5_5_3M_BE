package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ReviewUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewWrittenMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;

    @Override
    @Transactional
    public void handleReviewWritten(ReviewWrittenMessage msg) {
        Movie movie = movieRepository.findByMovieId(msg.movieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        Review review = Review.builder()
                .reviewId(msg.reviewId())
                .userId(msg.userId())
                .nickname(msg.nickname())
                .rating(msg.rating())
                .comment(msg.content())
                .createdAt(LocalDateTime.now())
                .movieId(msg.movieId())
                .status(Review.ReviewStatus.CREATE)
                .build();
        reviewRepository.save(review);
        movie.applyReviewCreated(msg.rating());
    }

    @Override
    @Transactional
    public void handleReviewUpdated(ReviewUpdatedMessage msg) {
        Review review = reviewRepository.findById(msg.reviewId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_FOUND));
        Movie movie = movieRepository.findByMovieId(review.getMovieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        Integer oldRating = review.getRating();
        review.update(msg.rating(), msg.content());
        movie.applyReviewUpdated(oldRating, msg.rating());
    }

    @Override
    @Transactional
    public void handleReviewDeleted(ReviewDeletedMessage msg) {
        Review review = reviewRepository.findById(msg.reviewId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW_NOT_FOUND));
        Movie movie = movieRepository.findByMovieId(review.getMovieId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        reviewRepository.delete(review);
        movie.applyReviewDeleted(review.getRating());
    }
}
