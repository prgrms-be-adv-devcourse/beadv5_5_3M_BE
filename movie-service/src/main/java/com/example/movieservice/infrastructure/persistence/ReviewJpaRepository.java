package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
}
