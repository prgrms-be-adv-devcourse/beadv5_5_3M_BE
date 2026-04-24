package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.enums.InteractionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserInteractionHistoryJpaRepository extends JpaRepository<UserInteractionHistory, Long> {

    @Query("SELECT h FROM UserInteractionHistory h WHERE h.userId = :userId ORDER BY h.createdAt DESC")
    List<UserInteractionHistory> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT id, user_id, movie_id, interaction_type, schedule_id, created_at FROM (
                  SELECT id, user_id, movie_id, interaction_type, schedule_id, created_at,
                         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
                  FROM user_interaction_history
                  WHERE user_id IN :userIds
              ) ranked WHERE rn <= :limit
            """, nativeQuery = true)
    List<UserInteractionHistory> findRecentByUserIds(
            @Param("userIds") List<UUID> userIds, @Param("limit") int limit);

    @Query("SELECT DISTINCT h.movieId FROM UserInteractionHistory h WHERE h.userId = :userId")
    List<Long> findDistinctMovieIdsByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    boolean existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(UUID userId, Long movieId, InteractionType interactionType, Long scheduleId);

    void deleteByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    @Modifying
    @Query("DELETE FROM UserInteractionHistory h WHERE h.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
