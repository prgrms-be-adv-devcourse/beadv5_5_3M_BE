package com.example.aiservice.application.batch;

import com.example.aiservice.domain.model.UserPreference;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import com.example.aiservice.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * recommended_log 정리 및 epsilon 갱신 컴포넌트.
 * BatchScheduler self-invocation 시 @Transactional이 동작하지 않는 문제를 우회하기 위해 별도 Bean으로 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPreprocessingService {

    @Value("${batch.diversity-window-min-days}")
    private int diversityWindowMinDays;

    @Value("${batch.diversity-window-max-days}")
    private int diversityWindowMaxDays;

    @Value("${batch.ctr-window-days}")
    private int ctrWindowDays;

    @Value("${batch.base-epsilon}")
    private double baseEpsilon;

    @Value("${batch.default-exploration-rate}")
    private double defaultExplorationRate;

    private static final int DAILY_RECOMMENDATION_COUNT = 15;

    private final MovieEmbeddedRepository movieEmbeddedRepository;
    private final RecommendedLogRepository recommendedLogRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional
    public void cleanupRecommendedLog() {
        long movieCount = movieEmbeddedRepository.count();
        int diversityWindowDays = (int) Math.max(diversityWindowMinDays,
                Math.min(diversityWindowMaxDays, movieCount / (double) DAILY_RECOMMENDATION_COUNT));

        int retentionDays = Math.max(diversityWindowDays, ctrWindowDays);
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        recommendedLogRepository.deleteOlderThan(cutoff);
        log.info("[Batch] recommended_log 정리 완료 - diversity: {}일, ctr: {}일, 보존: {}일, 기준일: {}",
                diversityWindowDays, ctrWindowDays, retentionDays, cutoff);
    }

    @Transactional
    public void updateEpsilon(List<UUID> targetUserIds) {
        LocalDate ctrCutoff = LocalDate.now().minusDays(ctrWindowDays);
        Map<UUID, Double> ctrMap = recommendedLogRepository.findExplorationCtrPerUser(ctrCutoff, targetUserIds);

        List<UserPreference> users = userPreferenceRepository.findAllByIds(targetUserIds);
        for (UserPreference user : users) {
            double explorationRate = ctrMap.getOrDefault(user.getUserId(), defaultExplorationRate);
            double trustFactor = 1 + Math.log(1 + user.getWatchCount());
            double epsilon = (baseEpsilon / trustFactor) * (1 + explorationRate);
            userPreferenceRepository.updateEpsilonAndCtr(user.getUserId(), epsilon, explorationRate);
        }
        log.info("[Batch] epsilon 갱신 완료 - 처리 유저: {}명", users.size());
    }
}
