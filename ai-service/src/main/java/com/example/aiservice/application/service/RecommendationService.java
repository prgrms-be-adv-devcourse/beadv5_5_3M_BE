package com.example.aiservice.application.service;

import com.example.aiservice.application.batch.RecommendationTrigger;
import com.example.aiservice.application.usecase.RecommendationUseCase;
import com.example.aiservice.domain.model.RecommendedLog;
import com.example.aiservice.domain.model.RecommendedMovie;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import com.example.aiservice.domain.repository.RecommendedMovieRepository;
import com.example.aiservice.global.exception.ErrorStatus;
import com.example.aiservice.global.exception.GeneralException;
import com.example.aiservice.infrastructure.redis.RedisRecommendationClient;
import com.example.aiservice.presentation.dto.response.RecommendationItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService implements RecommendationUseCase {

    private final RecommendedMovieRepository recommendedMovieRepository;
    private final RecommendedLogRepository recommendedLogRepository;
    private final RedisRecommendationClient redisRecommendationClient;
    private final RecommendationTrigger recommendationTrigger;

    @Override
    @Transactional
    public List<RecommendationItemResponse> getRecommendations(UUID userId) {
        // 1. Cache Hit → 즉시 반환
        List<RecommendationItemResponse> cached = redisRecommendationClient.get(userId);
        if (cached != null) {
            log.debug("[Recommendation] Cache Hit - userId: {}", userId);
            return cached;
        }

        // 2. Cache Miss → DB 조회
        log.debug("[Recommendation] Cache Miss - userId: {}", userId);
        List<RecommendedMovie> movies = recommendedMovieRepository.findTop10ByUserIdOrderByRank(userId);

        // 3. recommended_log INSERT (ON CONFLICT DO NOTHING) + logId 조회
        LocalDate today = LocalDate.now();
        insertRecommendedLogs(userId, movies, today);
        Map<Long, Long> movieIdToLogId = recommendedLogRepository
                .findByUserIdAndMovieIdsAndDate(userId, toMovieIds(movies), today)
                .stream()
                .collect(Collectors.toMap(RecommendedLog::getMovieId, RecommendedLog::getId));

        List<RecommendationItemResponse> result = movies.stream()
                .map(m -> new RecommendationItemResponse(
                        movieIdToLogId.get(m.getId().getMovieId()),
                        m.getId().getMovieId()
                ))
                .toList();

        // 4. 결과 7개 이하 → 백그라운드 재계산 트리거, Redis 캐싱 생략
        //    캐싱하면 재계산 완료 후에도 Cache Hit으로 부족한 결과가 계속 반환되므로
        //    캐시 미스 상태를 유지해서 재계산 완료 후 새 데이터가 반영되도록 함
        if (result.size() <= 7) {
            log.warn("[Recommendation] 추천 결과 부족 ({}개), 백그라운드 재계산 트리거 - userId: {}", result.size(), userId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recommendationTrigger.trigger(userId);
                }
            });
            return result;
        }

        // 5. Redis 캐싱 (결과가 충분한 경우에만)
        redisRecommendationClient.set(userId, result);

        return result;
    }

    @Override
    @Transactional
    public void click(UUID userId, Long logId) {
        int updated = recommendedLogRepository.markAsClicked(logId, userId);
        if (updated == 0) {
            throw new GeneralException(ErrorStatus.RECOMMENDATION_LOG_NOT_FOUND);
        }
    }

    private void insertRecommendedLogs(UUID userId, List<RecommendedMovie> movies, LocalDate today) {
        movies.forEach(m -> recommendedLogRepository.insertOnConflictDoNothing(
                userId,
                m.getId().getMovieId(),
                m.isExploration(),
                m.getExplorationSource() != null ? m.getExplorationSource().name() : null,
                today
        ));
    }

    private List<Long> toMovieIds(List<RecommendedMovie> movies) {
        return movies.stream().map(m -> m.getId().getMovieId()).toList();
    }
}
