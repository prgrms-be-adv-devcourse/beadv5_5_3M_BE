package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.MovieSyncUseCase;
import com.example.aiservice.domain.model.*;
import com.example.aiservice.domain.model.enums.Gender;
import com.example.aiservice.domain.repository.*;
import com.example.aiservice.infrastructure.embedding.EmbeddingClient;
import com.example.aiservice.infrastructure.embedding.EmbeddingResult;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.aiservice.infrastructure.redis.RedisRecommendationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieSyncService implements MovieSyncUseCase {

    // movie_statistics 초기화 기준: 0세~100세 이상, 10세 단위
    private static final List<Integer> AGE_GROUPS = List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);

    private final MovieEmbeddedRepository movieEmbeddedRepository;
    private final MovieStatisticsRepository movieStatisticsRepository;
    private final RecommendedMovieRepository recommendedMovieRepository;
    private final EmbeddingClient embeddingClient;
    private final RedisRecommendationClient redisRecommendationClient;

    @Override
    @Transactional
    public void handleMovieCreated(MovieCreatedMessage msg) {
        if (movieEmbeddedRepository.existsById(msg.movieId())) {
            log.warn("[Kafka] 중복 메시지 skip - movieId: {}", msg.movieId());
            return;
        }

        // 1. EmbeddingClient: description → 영어 summary + 영어 카테고리 + embedding
        EmbeddingResult result = embeddingClient.embed(msg.description(), msg.category());

        // 2. movies_embedded INSERT (영어 카테고리, is_public=false, published_at=NULL)
        MovieEmbedded movieEmbedded = MovieEmbedded.builder()
                .movieId(msg.movieId())
                .embedding(result.embedding())
                .summary(result.summary())
                .category(result.categoriesEn())
                .isPublic(false)
                .build();
        movieEmbeddedRepository.save(movieEmbedded);

        // 3. movie_statistics INSERT: age_group(11) × gender(2) = 22행, 모두 0으로 초기화
        List<MovieStatistics> stats = AGE_GROUPS.stream()
                .flatMap(ageGroup -> Arrays.stream(Gender.values())
                        .map(gender -> MovieStatistics.builder()
                                .id(new MovieStatisticsId(msg.movieId(), ageGroup, gender))
                                .watchCount(0)
                                .totalCount(0)
                                .build()))
                .collect(Collectors.toList());
        movieStatisticsRepository.saveAll(stats);

        log.info("[Kafka] movie.created 처리 완료 - movieId: {}", msg.movieId());
    }

    @Override
    @Transactional
    public void handleMovieDeleted(MovieDeletedMessage msg) {
        if (!movieEmbeddedRepository.existsById(msg.movieId())) {
            log.warn("[Kafka] movie.deleted - 존재하지 않는 movieId skip: {}", msg.movieId());
            return;
        }

        // 1. 삭제 전 영향받는 유저 목록 조회 (삭제 후엔 조회 불가)
        List<UUID> affectedUserIds = recommendedMovieRepository.findUserIdsByMovieId(msg.movieId());

        // 2. DB hard DELETE (의존성 역순)
        // user_interaction_history는 삭제하지 않음 (K-Means 계산 시 movies_embedded JOIN에서 자연 필터링)
        recommendedMovieRepository.deleteByMovieId(msg.movieId());
        movieStatisticsRepository.deleteByMovieId(msg.movieId());
        movieEmbeddedRepository.deleteById(msg.movieId());

        // 3. Redis 캐시 삭제 (best effort — 실패 시 TTL 24h로 자연 만료)
        affectedUserIds.forEach(redisRecommendationClient::deleteCache);

        log.info("[Kafka] movie.deleted 처리 완료 - movieId: {}, 캐시 삭제 유저 수: {}",
                msg.movieId(), affectedUserIds.size());
    }
}
