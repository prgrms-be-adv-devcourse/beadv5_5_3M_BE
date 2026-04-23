package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.enums.InteractionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserInteractionHistoryJpaRepository extends JpaRepository<UserInteractionHistory, Long> {

    boolean existsByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    boolean existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(UUID userId, Long movieId, InteractionType interactionType, Long scheduleId);

    void deleteByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    @Modifying
    @Query("DELETE FROM UserInteractionHistory h WHERE h.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
