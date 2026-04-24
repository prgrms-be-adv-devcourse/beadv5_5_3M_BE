package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.ReviewAuthorization;
import com.example.movieservice.domain.repository.ReviewAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewAuthorizationRepositoryAdapter implements ReviewAuthorizationRepository {

    private final ReviewAuthorizationJpaRepository jpaRepository;

    @Override
    public void save(ReviewAuthorization authorization) {
        jpaRepository.save(authorization);
    }

    @Override
    public boolean existsByTicketId(Long ticketId) {
        return jpaRepository.existsByTicketId(ticketId);
    }

    @Override
    public boolean existsByUserIdAndMovieId(UUID userId, Long movieId) {
        return jpaRepository.existsByUserIdAndMovieId(userId, movieId);
    }

    @Override
    public Optional<ReviewAuthorization> findFirstByUserIdAndMovieIdAndUsedFalse(UUID userId, Long movieId) {
        return jpaRepository.findFirstByUserIdAndMovieIdAndUsedFalseOrderByAuthorizedAt(userId, movieId);
    }

    @Override
    public Optional<ReviewAuthorization> findByUserIdAndScheduleId(UUID userId, Long scheduleId) {
        return jpaRepository.findByUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}