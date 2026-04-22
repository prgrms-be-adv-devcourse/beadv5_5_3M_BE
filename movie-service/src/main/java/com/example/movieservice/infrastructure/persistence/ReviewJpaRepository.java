package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.MovieRatingStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findTop5ByMovieIdOrderByUpdatedAtDesc(Long movieId);
    Page<Review> findByMovieId(Long movieId, Pageable pageable);
    Page<Review> findByUserId(UUID userId, Pageable pageable);
    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);

    @Query("SELECT r.movieId AS movieId, COUNT(r) AS reviewCount, AVG(r.rating) AS averageRating FROM Review r GROUP BY r.movieId")
    List<MovieRatingStats> findRatingStatsByMovieId();
}