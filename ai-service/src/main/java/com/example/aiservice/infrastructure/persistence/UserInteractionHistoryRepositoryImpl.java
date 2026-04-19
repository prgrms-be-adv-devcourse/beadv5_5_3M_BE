package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserInteractionHistoryId;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserInteractionHistoryRepositoryImpl implements UserInteractionHistoryRepository {

    private final UserInteractionHistoryJpaRepository userInteractionHistoryJpaRepository;

    @Override
    public boolean existsById(UserInteractionHistoryId id) {
        return userInteractionHistoryJpaRepository.existsById(id);
    }

    @Override
    public void insertOnConflictDoNothing(UUID userId, Long movieId, String interactionType, LocalDateTime createdAt) {
        userInteractionHistoryJpaRepository.insertOnConflictDoNothing(userId, movieId, interactionType, createdAt);
    }

    @Override
    public void deleteById(UserInteractionHistoryId id) {
        userInteractionHistoryJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        userInteractionHistoryJpaRepository.deleteByUserId(userId);
    }
}
