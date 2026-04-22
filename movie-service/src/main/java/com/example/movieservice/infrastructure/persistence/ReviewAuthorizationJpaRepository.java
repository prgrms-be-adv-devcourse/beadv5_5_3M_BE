package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.ReviewAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAuthorizationJpaRepository extends JpaRepository<ReviewAuthorization, Long> {
    boolean existsByTicketId(Long ticketId);
    boolean existsByUserIdAndMovieId(UUID userId, Long movieId);
    Optional<ReviewAuthorization> findByUserIdAndScheduleId(UUID userId, Long scheduleId);
    void deleteByUserId(UUID userId);
}