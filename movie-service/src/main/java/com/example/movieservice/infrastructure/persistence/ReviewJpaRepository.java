package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findTop5ByMovieIdOrderByUpdatedAtDesc(Long movieId);
}
