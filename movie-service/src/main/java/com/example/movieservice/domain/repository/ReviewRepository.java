package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {
    boolean existsById(Long reviewId);
    Optional<Review> findById(Long reviewId);
    Review save(Review review);
    void delete(Review review);
    List<Review> findTop5ByMovieId(Long movieId);
    List<MovieRatingStats> findAllRatingStats();
    Page<Review> findByMovieId(Long movieId, Pageable pageable);
    Page<Review> findByUserId(UUID userId, Pageable pageable);
    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);
}