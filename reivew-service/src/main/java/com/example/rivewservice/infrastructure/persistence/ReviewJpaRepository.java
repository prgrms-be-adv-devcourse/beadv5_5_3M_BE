package com.example.rivewservice.infrastructure.persistence;

import com.example.rivewservice.domain.enums.ReviewStatus;
import com.example.rivewservice.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    Optional<Review> findFirstByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag);
    boolean existsByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag);
    Page<Review> findAllByMovieIdAndFlagFalse(Long movieId, Pageable pageable);
    Page<Review> findAllByUserUserIdAndFlagFalse(UUID userId, Pageable pageable);
    boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId);
    boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId);
    boolean existsByTicketId(Long ticketId);
    Optional<Review> findFirstByUserUserIdAndMovieIdAndStatusAndFlagIsTrue(
            UUID userId, Long movieId, ReviewStatus status
    );
}
