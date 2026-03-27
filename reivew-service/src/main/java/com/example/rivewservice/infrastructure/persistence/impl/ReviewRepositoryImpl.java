package com.example.rivewservice.infrastructure.persistence.impl;

import com.example.rivewservice.domain.model.Review;
import com.example.rivewservice.domain.repository.ReviewRepository;
import com.example.rivewservice.infrastructure.persistence.ReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {
    private final ReviewJpaRepository reviewJpaRepository;

    @Override
    public Review save(Review review) {
        return reviewJpaRepository.save(review);
    }

    @Override
    public Optional<Review> findById(Long reviewId) {
        return reviewJpaRepository.findById(reviewId);
    }

    @Override
    public boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId) {
        return reviewJpaRepository.existsByUserUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId) {
        return reviewJpaRepository.existsByUserUserIdAndMovieId(userId, movieId);
    }
}
