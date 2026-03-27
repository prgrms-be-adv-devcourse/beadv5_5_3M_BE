package com.example.rivewservice.domain.repository;

import com.example.rivewservice.domain.model.Review;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(Long reviewId);

    boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId);

    boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId);
}