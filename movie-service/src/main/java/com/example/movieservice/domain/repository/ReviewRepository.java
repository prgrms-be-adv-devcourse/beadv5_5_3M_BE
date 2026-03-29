package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository {
    boolean existsById(Long reviewId);
    Optional<Review> findById(Long reviewId);
    void save(Review review);
    void delete(Review review);
    List<Review> findTop5ByMovieId(Long movieId);
}
