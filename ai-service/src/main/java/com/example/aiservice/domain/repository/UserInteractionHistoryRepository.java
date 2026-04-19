package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.UserInteractionHistoryId;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UserInteractionHistoryRepository {

    boolean existsById(UserInteractionHistoryId id);

    void insertOnConflictDoNothing(UUID userId, Long movieId, String interactionType, LocalDateTime createdAt);

    void deleteById(UserInteractionHistoryId id);

    void deleteByUserId(UUID userId);
}
