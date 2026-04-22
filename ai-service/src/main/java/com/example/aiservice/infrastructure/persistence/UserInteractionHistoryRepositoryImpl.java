package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.enums.InteractionType;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserInteractionHistoryRepositoryImpl implements UserInteractionHistoryRepository {

    private final UserInteractionHistoryJpaRepository userInteractionHistoryJpaRepository;

    @Override
    public List<UserInteractionHistory> findRecentByUserId(UUID userId, int limit) {
        return userInteractionHistoryJpaRepository.findRecentByUserId(userId, PageRequest.of(0, limit));
    }

    @Override
    public boolean existsByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType) {
        return userInteractionHistoryJpaRepository.existsByUserIdAndMovieIdAndInteractionType(userId, movieId, interactionType);
    }

    @Override
    public boolean existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(UUID userId, Long movieId, InteractionType interactionType, Long scheduleId) {
        return userInteractionHistoryJpaRepository.existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(userId, movieId, interactionType, scheduleId);
    }

    @Override
    public void save(UserInteractionHistory history) {
        userInteractionHistoryJpaRepository.save(history);
    }

    @Override
    public void deleteByUserIdAndMovieIdAndInteractionType(UUID userId, Long movieId, InteractionType interactionType) {
        userInteractionHistoryJpaRepository.deleteByUserIdAndMovieIdAndInteractionType(userId, movieId, interactionType);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        userInteractionHistoryJpaRepository.deleteByUserId(userId);
    }
}
