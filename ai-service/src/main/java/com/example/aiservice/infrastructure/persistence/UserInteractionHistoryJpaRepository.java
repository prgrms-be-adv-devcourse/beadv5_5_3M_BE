package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.UserInteractionHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UserInteractionHistoryJpaRepository extends JpaRepository<UserInteractionHistory, UserInteractionHistoryId> {

    @Modifying
    @Query(
            value = "INSERT INTO user_interaction_history (user_id, movie_id, interaction_type, created_at) " +
                    "VALUES (:userId, :movieId, :interactionType, :createdAt) ON CONFLICT DO NOTHING",
            nativeQuery = true
    )
    void insertOnConflictDoNothing(
            @Param("userId") UUID userId,
            @Param("movieId") Long movieId,
            @Param("interactionType") String interactionType,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Modifying
    @Query("DELETE FROM UserInteractionHistory h WHERE h.id.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
