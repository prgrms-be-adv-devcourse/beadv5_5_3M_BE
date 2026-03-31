package com.example.rivewservice.domain.repository;

import com.example.rivewservice.domain.enums.ReviewStatus;
import com.example.rivewservice.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(Long reviewId);

    Optional<Review> findFirstByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag);

    boolean existsByUserUserIdAndMovieIdAndFlag(UUID userId, Long movieId, Boolean flag);

    Page<Review> findAllByMovieIdAndFlagFalse(Long movieId, Pageable pageable);

    Page<Review> findAllByUserIdAndFlagFalse(UUID userId, Pageable pageable);

    boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId);

    boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId);

    boolean existsByTicketId(Long ticketId);

    Optional<Review> findFirstByUserUserIdAndMovieIdAndStatusAndFlagIsTrue(
            UUID userId, Long movieId, ReviewStatus status
    );
}