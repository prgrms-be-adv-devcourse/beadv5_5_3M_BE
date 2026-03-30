package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.MovieRatingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findTop5ByMovieIdOrderByUpdatedAtDesc(Long movieId);

    @Query("SELECT r.movieId AS movieId, COUNT(r) AS reviewCount, AVG(r.rating) AS averageRating FROM Review r GROUP BY r.movieId")
    List<MovieRatingStats> findRatingStatsByMovieId();
}
