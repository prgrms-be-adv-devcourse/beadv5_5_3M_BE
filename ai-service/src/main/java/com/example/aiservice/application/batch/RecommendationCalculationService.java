package com.example.aiservice.application.batch;

import com.example.aiservice.domain.model.*;
import com.example.aiservice.domain.model.enums.ExplorationSource;
import com.example.aiservice.domain.repository.*;
import com.example.aiservice.infrastructure.llm.LlmRerankingClient;
import com.example.aiservice.infrastructure.redis.RedisBatchStateClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCalculationService {

    // 착취 후보
    private static final int ANN_FETCH_PER_CLUSTER = 15; // 클러스터당 ANN 최소 조회량

    // 탐색 후보 (cold start — epsilon 없으므로 고정값)
    private static final int COLD_DEMOGRAPHIC_TARGET = 8;
    private static final int COLD_NEW_RELEASE_TARGET = 4;
    private static final int COLD_LOW_EXPOSURE_TARGET = 3;

    // 탐색 비율 (non-cold-start — epsilon으로 동적 결정)
    // epsilon=baseEpsilon(신규 유저) → MAX_EXPLORATION_RATIO (탐색 65%)
    // epsilon→0(헤비 유저)          → MIN_EXPLORATION_RATIO (탐색 30%)
    private static final double MIN_EXPLORATION_RATIO = 0.30;
    private static final double MAX_EXPLORATION_RATIO = 0.65;
    private static final double DEMO_RATIO = 0.50;        // 탐색 후보 내 demographic 비중
    private static final double NEW_RELEASE_RATIO = 0.30; // 탐색 후보 내 new_release 비중
    // LOW_EXPOSURE: 나머지 20%

    // 탐색 후보 DB 조회량 (필터링 후 target 개수 확보를 위해 넉넉하게 조회)
    private static final int DEMO_FETCH = 50;
    private static final int NEW_RELEASE_FETCH = 30;
    private static final int LOW_EXPOSURE_FETCH = 24;

    private static final int MAX_CANDIDATES = 70;
    private static final int FINAL_RANK_COUNT = 15;
    private static final int DAILY_RECOMMENDATION_COUNT = 15;

    @Value("${batch.diversity-window-min-days}")
    private int diversityWindowMinDays;

    @Value("${batch.diversity-window-max-days}")
    private int diversityWindowMaxDays;

    @Value("${batch.base-epsilon}")
    private double baseEpsilon;

    private final MovieEmbeddedRepository movieEmbeddedRepository;
    private final MovieStatisticsRepository movieStatisticsRepository;
    private final UserInteractionHistoryRepository userInteractionHistoryRepository;
    private final RecommendedLogRepository recommendedLogRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final LlmRerankingClient llmRerankingClient;
    private final RedisBatchStateClient redisBatchStateClient;
    private final RecommendationSaver recommendationSaver;

    /**
     * 전체 유저 추천 계산 — OpenAI Batch API 제출.
     * BatchScheduler에서 K-Means/epsilon 갱신 후 호출.
     */
    public void submitBatchForAllUsers(List<UUID> targetUserIds) {
        long movieCount = movieEmbeddedRepository.count();
        LocalDate diversityCutoff = calculateDiversityCutoff(movieCount);

        List<UserPreference> users = userPreferenceRepository.findAllByIds(targetUserIds);
        List<String> jsonlLines = new ArrayList<>();

        for (UserPreference user : users) {
            boolean isColdStart = user.getCluster() == null || user.getCluster().isEmpty();

            if (isColdStart) {
                // cold start: 착취 후보 없음 + 인구통계 동일 → LLM 불필요, 즉시 벡터 fallback 처리
                try {
                    List<CandidateMovie> candidates = buildCandidates(user, diversityCutoff);
                    List<CandidateMovie> ranked = rankWithVector(candidates, user);
                    recommendationSaver.save(user.getUserId(),
                            ranked.stream().map(CandidateMovie::movieId).toList(),
                            extractMeta(ranked));
                } catch (Exception e) {
                    log.error("[Batch] cold start 유저 {} 처리 실패", user.getUserId(), e);
                }
                continue;
            }

            try {
                List<CandidateMovie> candidates = buildCandidates(user, diversityCutoff);
                if (candidates.isEmpty()) continue;

                Map<Long, CandidateInfo> meta = extractMeta(candidates);
                redisBatchStateClient.saveCandidateMeta(user.getUserId(), meta);

                jsonlLines.add(llmRerankingClient.buildBatchLine(
                        user.getUserId().toString(), candidates, user, FINAL_RANK_COUNT));
            } catch (Exception e) {
                log.error("[Batch] 유저 {} 배치 라인 생성 실패", user.getUserId(), e);
            }
        }

        if (jsonlLines.isEmpty()) {
            log.warn("[Batch] 배치 제출 대상 없음");
            return;
        }

        try {
            String batchId = llmRerankingClient.submitBatch(jsonlLines);
            redisBatchStateClient.saveBatchId(batchId);
            redisBatchStateClient.saveTargetUserIds(targetUserIds);
            log.info("[Batch] 추천 배치 제출 완료 - batchId: {}, 유저: {}명", batchId, jsonlLines.size());
        } catch (Exception e) {
            log.error("[Batch] 배치 API 제출 실패 - 벡터 fallback 시작", e);
            calculateWithVectorFallbackInternal(diversityCutoff, users);
        }
    }

    /**
     * 전날 제출한 Batch API 결과를 수령하고 recommended_movie를 갱신한다.
     * BatchScheduler 02:00에 호출.
     */
    public void processBatchResults() {
        String batchId = redisBatchStateClient.getBatchId();
        if (batchId == null) {
            log.info("[Batch] 처리할 배치 없음 (최초 실행 또는 이미 처리됨)");
            return;
        }

        boolean completed;
        try {
            completed = llmRerankingClient.isBatchCompleted(batchId);
        } catch (Exception e) {
            log.error("[Batch] 배치 상태 조회 실패 - batchId: {}", batchId, e);
            completed = false;
        }

        if (!completed) {
            log.warn("[Batch] 배치 24h 내 미완료 - batchId: {}, 벡터 fallback 시작", batchId);
            long movieCount = movieEmbeddedRepository.count();
            LocalDate diversityCutoff = calculateDiversityCutoff(movieCount);
            List<UUID> targetUserIds = redisBatchStateClient.getTargetUserIds();

            if (targetUserIds.isEmpty()) {
                log.error("[Batch] 대상 유저 목록 없음 (Redis 장애 가능성) - 전체 유저로 fallback");
            }

            List<UserPreference> fallbackUsers = targetUserIds.isEmpty()
                    ? userPreferenceRepository.findAll()
                    : userPreferenceRepository.findAllByIds(targetUserIds);

            calculateWithVectorFallbackInternal(diversityCutoff, fallbackUsers);
            redisBatchStateClient.clear();
            return;
        }

        Map<String, List<Long>> results;
        try {
            results = llmRerankingClient.getBatchResults(batchId);
        } catch (Exception e) {
            log.error("[Batch] 배치 결과 수령 실패 - batchId: {}", batchId, e);
            redisBatchStateClient.clear();
            return;
        }

        for (Map.Entry<String, List<Long>> entry : results.entrySet()) {
            try {
                UUID userId = UUID.fromString(entry.getKey());
                Map<Long, CandidateInfo> meta = redisBatchStateClient.getCandidateMeta(userId);
                if (meta.isEmpty()) {
                    // Redis TTL 만료 등으로 meta 유실 → 벡터 기반 재계산
                    log.warn("[Batch] 유저 {} candidate meta 없음 (Redis TTL 만료 가능성) - 벡터 fallback", entry.getKey());
                    calculateWithVectorFallback(userId);
                    continue;
                }
                recommendationSaver.save(userId, entry.getValue(), meta);
            } catch (Exception e) {
                log.error("[Batch] 유저 {} 결과 저장 실패", entry.getKey(), e);
            }
        }

        redisBatchStateClient.clear();
        log.info("[Batch] 추천 계산 완료 - 처리 유저: {}명", results.size());
    }

    /**
     * 단일 유저 재계산. RecommendationTrigger (추천 결과 7개 이하 시 백그라운드 재계산) 에서 호출.
     * non-cold-start: LLM 즉시 호출 → 실패 시 벡터 fallback.
     * cold-start: 벡터 기반 버킷 순서 유지.
     */
    public void calculateWithVectorFallback(UUID userId) {
        UserPreference user = userPreferenceRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[Fallback] user_preference 없음 - userId: {}", userId);
            return;
        }

        long movieCount = movieEmbeddedRepository.count();
        LocalDate diversityCutoff = calculateDiversityCutoff(movieCount);
        List<CandidateMovie> candidates = buildCandidates(user, diversityCutoff);

        boolean isColdStart = user.getCluster() == null || user.getCluster().isEmpty();

        if (isColdStart) {
            List<CandidateMovie> ranked = rankWithVector(candidates, user);
            recommendationSaver.save(userId, ranked.stream().map(CandidateMovie::movieId).toList(), extractMeta(ranked));
            return;
        }

        // non-cold-start: LLM 즉시 호출, 실패 시 벡터 fallback
        try {
            List<Long> rankedIds = llmRerankingClient.rerankImmediate(candidates, user, FINAL_RANK_COUNT);
            recommendationSaver.save(userId, rankedIds, extractMeta(candidates));
        } catch (Exception e) {
            log.warn("[Fallback] LLM 즉시 호출 실패, 벡터 fallback - userId: {}", userId, e);
            List<CandidateMovie> ranked = rankWithVector(candidates, user);
            recommendationSaver.save(userId, ranked.stream().map(CandidateMovie::movieId).toList(), extractMeta(ranked));
        }
    }

    // ===

    private void calculateWithVectorFallbackInternal(LocalDate diversityCutoff, List<UserPreference> users) {
        for (UserPreference user : users) {
            try {
                List<CandidateMovie> candidates = buildCandidates(user, diversityCutoff);
                List<CandidateMovie> ranked = rankWithVector(candidates, user);
                List<Long> rankedIds = ranked.stream().map(CandidateMovie::movieId).toList();
                recommendationSaver.save(user.getUserId(), rankedIds, extractMeta(ranked));
            } catch (Exception e) {
                log.error("[Fallback] 유저 {} 계산 실패", user.getUserId(), e);
            }
        }
        log.info("[Fallback] 벡터 기반 추천 계산 완료 - 처리 유저: {}명", users.size());
    }

    private List<CandidateMovie> buildCandidates(UserPreference user, LocalDate diversityCutoff) {
        boolean isColdStart = user.getCluster() == null || user.getCluster().isEmpty();

        Set<Long> fullExclude = getExcludeIds(user.getUserId(), diversityCutoff);
        List<CandidateMovie> candidates = buildCandidatesWithExclude(user, fullExclude, isColdStart);

        if (candidates.size() < FINAL_RANK_COUNT) {
            log.info("[Batch] 후보 부족 ({}) - 다양성 윈도우 완화 - userId: {}", candidates.size(), user.getUserId());
            Set<Long> interactionOnlyExclude = new HashSet<>(
                    userInteractionHistoryRepository.findDistinctMovieIdsByUserId(user.getUserId()));
            candidates = buildCandidatesWithExclude(user, interactionOnlyExclude, isColdStart);
        }

        return candidates;
    }

    private List<CandidateMovie> buildCandidatesWithExclude(
            UserPreference user, Set<Long> excludeIds, boolean isColdStart) {
        List<CandidateMovie> candidates = new ArrayList<>();
        if (!isColdStart) {
            int explorationCount = calcExplorationCount(user.getEpsilon());
            int exploitationCount = MAX_CANDIDATES - explorationCount;
            candidates.addAll(buildExploitationCandidates(user.getCluster(), excludeIds, exploitationCount));
        }
        candidates.addAll(buildExplorationCandidates(user, excludeIds, isColdStart));
        return deduplicate(candidates, MAX_CANDIDATES);
    }

    private Set<Long> getExcludeIds(UUID userId, LocalDate diversityCutoff) {
        Set<Long> excludeIds = new HashSet<>(
                userInteractionHistoryRepository.findDistinctMovieIdsByUserId(userId));
        excludeIds.addAll(
                recommendedLogRepository.findRecentExposedMovieIds(userId, diversityCutoff));
        return excludeIds;
    }

    private List<CandidateMovie> buildExploitationCandidates(
            List<ClusterCenter> clusters, Set<Long> excludeIds, int target) {
        // 클러스터 중심점별 ANN 검색 → 합산 후 similarity 내림차순 정렬 → 상위 target개
        Map<Long, CandidateMovie> candidateMap = new LinkedHashMap<>();

        int fetchPerCluster = Math.max(
                (int) Math.ceil((double) target / clusters.size()),
                ANN_FETCH_PER_CLUSTER);

        for (ClusterCenter cluster : clusters) {
            List<AnnResult> annResults = movieEmbeddedRepository.findAnnNeighbors(
                    cluster.center(), fetchPerCluster);

            List<Long> movieIds = annResults.stream()
                    .map(AnnResult::movieId)
                    .filter(id -> !excludeIds.contains(id) && !candidateMap.containsKey(id))
                    .toList();

            Map<Long, MovieEmbedded> movieMap = movieEmbeddedRepository.findAllByIds(movieIds)
                    .stream()
                    .collect(Collectors.toMap(MovieEmbedded::getMovieId, m -> m));

            for (AnnResult ann : annResults) {
                MovieEmbedded movie = movieMap.get(ann.movieId());
                if (movie == null) continue;
                candidateMap.put(ann.movieId(), CandidateMovie.exploitation(
                        ann.movieId(), movie.getCategory(), movie.getSummary(), ann.similarity()));
            }
        }

        return candidateMap.values().stream()
                .sorted(Comparator.comparingDouble(CandidateMovie::similarity).reversed())
                .limit(target)
                .toList();
    }

    private List<CandidateMovie> buildExplorationCandidates(
            UserPreference user, Set<Long> excludeIds, boolean isColdStart) {

        int demoTarget, newReleaseTarget, lowExposureTarget;

        if (isColdStart) {
            demoTarget = COLD_DEMOGRAPHIC_TARGET;
            newReleaseTarget = COLD_NEW_RELEASE_TARGET;
            lowExposureTarget = COLD_LOW_EXPOSURE_TARGET;
        } else {
            int explorationCount = calcExplorationCount(user.getEpsilon());
            demoTarget = (int)(explorationCount * DEMO_RATIO);
            newReleaseTarget = (int)(explorationCount * NEW_RELEASE_RATIO);
            lowExposureTarget = explorationCount - demoTarget - newReleaseTarget;
        }

        List<CandidateMovie> result = new ArrayList<>();
        result.addAll(fetchExplorationMovies(
                movieStatisticsRepository.findDemographicCandidates(user.getAgeGroup(), user.getGender(), DEMO_FETCH),
                ExplorationSource.DEMOGRAPHIC, excludeIds, demoTarget));
        result.addAll(fetchExplorationMovies(
                movieEmbeddedRepository.findNewReleaseCandidates(NEW_RELEASE_FETCH),
                ExplorationSource.NEW_RELEASE, excludeIds, newReleaseTarget));
        result.addAll(fetchExplorationMovies(
                movieStatisticsRepository.findLowExposureCandidates(LOW_EXPOSURE_FETCH),
                ExplorationSource.LOW_EXPOSURE, excludeIds, lowExposureTarget));
        return result;
    }

    private List<CandidateMovie> fetchExplorationMovies(
            List<Long> movieIds, ExplorationSource source, Set<Long> excludeIds, int target) {

        List<Long> filteredIds = movieIds.stream()
                .filter(id -> !excludeIds.contains(id))
                .limit(target)
                .toList();

        Map<Long, MovieEmbedded> movieMap = movieEmbeddedRepository.findAllByIds(filteredIds)
                .stream()
                .collect(Collectors.toMap(MovieEmbedded::getMovieId, m -> m));

        return filteredIds.stream()
                .map(movieMap::get)
                .filter(Objects::nonNull)
                .map(m -> CandidateMovie.exploration(m.getMovieId(), m.getCategory(), m.getSummary(), source))
                .toList();
    }

    /**
     * 착취/탐색 중복 제거: 같은 movie_id면 착취 후보(isExploration=false)를 우선 유지.
     * 착취 후보가 먼저 추가되므로 탐색 후보가 기존 값을 덮어쓰려 할 때 기존 값(착취) 유지.
     */
    private List<CandidateMovie> deduplicate(List<CandidateMovie> candidates, int maxSize) {
        Map<Long, CandidateMovie> deduped = new LinkedHashMap<>();
        for (CandidateMovie c : candidates) {
            deduped.merge(c.movieId(), c, (existing, newOne) ->
                    existing.isExploration() ? newOne : existing);
        }
        return deduped.values().stream().limit(maxSize).toList();
    }

    /**
     * 벡터 기반 랭킹: non-cold start는 similarity score 내림차순, cold start는 버킷 순서 유지.
     * 배치 API 실패 fallback 및 cold start 유저에 사용.
     */
    private List<CandidateMovie> rankWithVector(List<CandidateMovie> candidates, UserPreference user) {
        boolean isColdStart = user.getCluster() == null || user.getCluster().isEmpty();

        if (isColdStart) {
            // cold start: 탐색 버킷 순서(demographic → new_release → low_exposure) 유지
            return candidates.stream().limit(FINAL_RANK_COUNT).toList();
        }

        List<CandidateMovie> exploitation = candidates.stream()
                .filter(c -> !c.isExploration())
                .sorted(Comparator.comparingDouble(CandidateMovie::similarity).reversed())
                .toList();

        List<CandidateMovie> exploration = candidates.stream()
                .filter(CandidateMovie::isExploration)
                .toList();

        List<CandidateMovie> combined = new ArrayList<>(exploitation);
        combined.addAll(exploration);
        return combined.stream().limit(FINAL_RANK_COUNT).toList();
    }

    private Map<Long, CandidateInfo> extractMeta(List<CandidateMovie> candidates) {
        return candidates.stream().collect(Collectors.toMap(
                CandidateMovie::movieId,
                c -> new CandidateInfo(c.isExploration(), c.explorationSource())
        ));
    }

    /**
     * epsilon을 탐색 후보 개수로 변환한다.
     * normalized = min(1, epsilon / baseEpsilon) 으로 [0, 1] 정규화.
     * epsilon=baseEpsilon(신규 유저) → MAX_EXPLORATION_RATIO(65%), epsilon→0(헤비 유저) → MIN_EXPLORATION_RATIO(30%).
     */
    private int calcExplorationCount(double epsilon) {
        double normalized = Math.min(1.0, epsilon / baseEpsilon);
        double ratio = MIN_EXPLORATION_RATIO + (MAX_EXPLORATION_RATIO - MIN_EXPLORATION_RATIO) * normalized;
        return (int)(MAX_CANDIDATES * ratio);
    }

    private LocalDate calculateDiversityCutoff(long movieCount) {
        int diversityWindowDays = (int) Math.max(diversityWindowMinDays,
                Math.min(diversityWindowMaxDays, movieCount / (double) DAILY_RECOMMENDATION_COUNT));
        return LocalDate.now().minusDays(diversityWindowDays);
    }
}
