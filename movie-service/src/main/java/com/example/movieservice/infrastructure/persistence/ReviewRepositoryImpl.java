package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.MovieRatingStats;
import com.example.movieservice.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewJpaRepository reviewJpaRepository;

    @Override
    public boolean existsById(Long reviewId) {
        return reviewJpaRepository.existsById(reviewId);
    }

    @Override
    public Optional<Review> findById(Long reviewId) {
        return reviewJpaRepository.findById(reviewId);
    }

    @Override
    public Review save(Review review) {
        return reviewJpaRepository.save(review);
    }

    @Override
    public void delete(Review review) {
        reviewJpaRepository.delete(review);
    }

    @Override
    public List<Review> findTop5ByMovieId(Long movieId) {
        return reviewJpaRepository.findTop5ByMovieIdOrderByUpdatedAtDesc(movieId);
    }

    @Override
    public List<MovieRatingStats> findAllRatingStats() {
        return reviewJpaRepository.findRatingStatsByMovieId();
    }

    @Override
    public Page<Review> findByMovieId(Long movieId, Pageable pageable) {
        return reviewJpaRepository.findByMovieId(movieId, pageable);
    }

    @Override
    public Page<Review> findByUserId(UUID userId, Pageable pageable) {
        return reviewJpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public boolean existsByUserIdAndMovieId(UUID userId, Long movieId) {
        return reviewJpaRepository.existsByUserIdAndMovieId(userId, movieId);
    }
}