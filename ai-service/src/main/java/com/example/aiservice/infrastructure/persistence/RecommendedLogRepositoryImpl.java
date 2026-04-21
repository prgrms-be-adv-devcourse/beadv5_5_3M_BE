package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.RecommendedLog;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecommendedLogRepositoryImpl implements RecommendedLogRepository {

    private final RecommendedLogJpaRepository recommendedLogJpaRepository;

    @Override
    public void insertOnConflictDoNothing(UUID userId, Long movieId, boolean isExploration, String explorationSource, LocalDate recommendedAt) {
        recommendedLogJpaRepository.insertOnConflictDoNothing(userId, movieId, isExploration, explorationSource, recommendedAt);
    }

    @Override
    public List<RecommendedLog> findByUserIdAndMovieIdsAndDate(UUID userId, List<Long> movieIds, LocalDate date) {
        return recommendedLogJpaRepository.findByUserIdAndMovieIdsAndDate(userId, movieIds, date);
    }

    @Override
    public int markAsClicked(Long logId, UUID userId) {
        return recommendedLogJpaRepository.markAsClicked(logId, userId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        recommendedLogJpaRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteOlderThan(LocalDate cutoff) {
        recommendedLogJpaRepository.deleteOlderThan(cutoff);
    }
}
