package com.example.aiservice.application.batch;

import com.example.aiservice.domain.model.CandidateInfo;
import com.example.aiservice.domain.model.RecommendedMovie;
import com.example.aiservice.domain.model.RecommendedMovieId;
import com.example.aiservice.domain.repository.RecommendedMovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 추천 결과 저장 컴포넌트.
 * RecommendationCalculationService는 유저 루프 내에서 이 컴포넌트를 호출한다.
 * @Transactional self-invocation 우회를 위해 별도 Bean으로 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationSaver {

    private final RecommendedMovieRepository recommendedMovieRepository;

    @Transactional
    public void save(UUID userId, List<Long> rankedIds, Map<Long, CandidateInfo> meta) {
        if (rankedIds.isEmpty()) {
            log.warn("[Saver] 저장할 추천 결과 없음 - userId: {}", userId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<RecommendedMovie> movies = new ArrayList<>();

        int rank = 1;
        for (Long movieId : rankedIds) {
            CandidateInfo info = meta.get(movieId);
            if (info == null) {
                log.warn("[Saver] 후보 외 movieId 무시 - userId: {}, movieId: {}", userId, movieId);
                continue;
            }
            movies.add(RecommendedMovie.builder()
                    .id(new RecommendedMovieId(movieId, userId))
                    .rank(rank++)
                    .isExploration(info.isExploration())
                    .explorationSource(info.explorationSource())
                    .createdAt(now)
                    .build());
        }

        if (movies.isEmpty()) {
            log.warn("[Saver] 유효한 추천 결과 없음 (전부 후보 외 movieId) - userId: {}", userId);
            return;
        }

        recommendedMovieRepository.upsertAll(userId, movies);
        log.debug("[Saver] 추천 결과 저장 완료 - userId: {}, 개수: {}", userId, movies.size());
    }
}
