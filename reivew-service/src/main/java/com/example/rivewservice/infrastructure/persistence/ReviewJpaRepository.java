package com.example.rivewservice.infrastructure.persistence;

import com.example.rivewservice.domain.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    boolean existsByUserUserIdAndScheduleId(UUID userId, Long scheduleId);
    boolean existsByUserUserIdAndMovieId(UUID userId, Long movieId);
}
