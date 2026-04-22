package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.ReviewAuthorization;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAuthorizationRepository {
    void save(ReviewAuthorization authorization);
    boolean existsByTicketId(Long ticketId);
    boolean existsByUserIdAndMovieId(UUID userId, Long movieId);
    Optional<ReviewAuthorization> findByUserIdAndScheduleId(UUID userId, Long scheduleId);
    void deleteByUserId(UUID userId);
}