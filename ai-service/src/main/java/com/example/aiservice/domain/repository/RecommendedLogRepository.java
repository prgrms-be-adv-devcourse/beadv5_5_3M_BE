package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.RecommendedLog;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecommendedLogRepository {

    void insertOnConflictDoNothing(UUID userId, Long movieId, boolean isExploration, String explorationSource, LocalDate recommendedAt);

    List<RecommendedLog> findByUserIdAndMovieIdsAndDate(UUID userId, List<Long> movieIds, LocalDate date);

    int markAsClicked(Long logId, UUID userId);

    void deleteByUserId(UUID userId);

    void deleteOlderThan(LocalDate cutoff);
}
