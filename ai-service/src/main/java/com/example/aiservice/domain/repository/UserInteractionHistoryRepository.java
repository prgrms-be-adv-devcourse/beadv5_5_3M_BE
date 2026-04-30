package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.enums.InteractionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserInteractionHistoryRepository {

    List<UserInteractionHistory> findRecentByUserId(UUID userId, int limit);

    Map<UUID, List<UserInteractionHistory>> findRecentByUserIds(List<UUID> userIds, int limit);

    List<Long> findDistinctMovieIdsByUserId(UUID userId);

    boolean existsByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    boolean existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(UUID userId, Long movieId, InteractionType interactionType, Long scheduleId);

    void save(UserInteractionHistory history);

    void deleteByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType);

    void deleteByUserId(UUID userId);
}
