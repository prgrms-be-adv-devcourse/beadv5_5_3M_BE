package com.example.rivewservice.infrastructure.persistence.impl;

import com.example.rivewservice.domain.model.Review;
import com.example.rivewservice.domain.repository.ReviewRepository;
import com.example.rivewservice.infrastructure.persistence.ReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Optional<Review> findFirstByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag) {
        return reviewJpaRepository.findFirstByUserUserIdAndMovieIdAndFlag(userId, movieId, flag);
    }

    @Override
    public boolean existsByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag) {
        return reviewJpaRepository.existsByUserUserIdAndMovieIdAndFlag(userId, movieId, flag);
    }

    @Override
    public Page<Review> findAllByMovieIdAndFlagFalse(Long movieId, Pageable pageable) {
        return reviewJpaRepository.findAllByMovieIdAndFlagFalse(movieId, pageable);
    }

    @Override
    public Page<Review> findAllByUserIdAndFlagFalse(UUID userId, Pageable pageable) {
        return reviewJpaRepository.findAllByUserUserIdAndFlagFalse(userId, pageable);
    }

    @Override
    public boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId) {
        return reviewJpaRepository.existsByUserUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId) {
        return reviewJpaRepository.existsByUserUserIdAndMovieId(userId, movieId);
    }

    @Override
    public boolean existsByTicketId(Long ticketId) {
        return reviewJpaRepository.existsByTicketId(ticketId);
    }
}