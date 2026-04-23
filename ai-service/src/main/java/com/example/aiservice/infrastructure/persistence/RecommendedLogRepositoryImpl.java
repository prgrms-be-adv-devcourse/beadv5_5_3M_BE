package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.RecommendedLog;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Override
    public Map<UUID, Double> findExplorationCtrPerUser(LocalDate cutoff, List<UUID> userIds) {
        String pgArray = "{" + userIds.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
        return recommendedLogJpaRepository.findExplorationCtrPerUser(cutoff, pgArray)
                .stream()
                .filter(p -> p.getExplorationClickRate() != null)
                .collect(Collectors.toMap(
                        p -> UUID.fromString(p.getUserId()),
                        ExplorationCtrProjection::getExplorationClickRate
                ));
    }

    @Override
    public List<Long> findRecentExposedMovieIds(UUID userId, LocalDate cutoff) {
        return recommendedLogJpaRepository.findRecentExposedMovieIds(userId, cutoff);
    }

    @Override
    public List<UUID> findActiveUserIds(LocalDate date) {
        return recommendedLogJpaRepository.findActiveUserIds(date);
    }
}
