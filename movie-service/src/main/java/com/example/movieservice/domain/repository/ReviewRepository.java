package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Review;

import java.util.Optional;

public interface ReviewRepository {
    Optional<Review> findById(Long reviewId);
    void save(Review review);
    void delete(Review review);
}
