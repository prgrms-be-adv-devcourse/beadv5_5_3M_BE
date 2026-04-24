package com.example.aiservice.application.batch;

import com.example.aiservice.domain.repository.RecommendedLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 30_000;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long MAX_DELAY_MS = 120_000;

    private final RecommendedLogRepository recommendedLogRepository;
    private final KMeansClusteringService kMeansClusteringService;
    private final RecommendationCalculationService recommendationCalculationService;
    private final BatchPreprocessingService batchPreprocessingService;

    /**
     * 00:00 — 배치 제출.
     * 어제 활성 유저 기준으로 epsilon/K-Means 갱신 후 OpenAI Batch API 제출.
     * 각 단계는 독립 실행 — 앞 단계 실패 시 최대 3회 재시도 후 다음 단계 진행.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void submitDailyBatch() {
        List<UUID> activeUserIds = recommendedLogRepository.findActiveUserIds(LocalDate.now().minusDays(1));
        log.info("[Batch] 어제 활성 유저: {}명", activeUserIds.size());

        executeWithRetry("로그 정리",      () -> batchPreprocessingService.cleanupRecommendedLog());
        executeWithRetry("epsilon 갱신",   () -> batchPreprocessingService.updateEpsilon(activeUserIds));
        execute("K-Means 재계산",          () -> kMeansClusteringService.recalculateClusters(activeUserIds));
        execute("배치 제출",               () -> recommendationCalculationService.submitBatchForAllUsers(activeUserIds));
    }

    /**
     * 02:00 — 배치 결과 수령.
     * 전날 00:30경 제출한 배치를 수령 (제출 후 ~26시간, 24h SLA 충분히 경과).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void receiveDailyBatchResults() {
        recommendationCalculationService.processBatchResults();
    }

    private void execute(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("[Batch] {} 실패", stepName, e);
        }
    }

    /**
     * 지수 백오프 재시도: 30s → 60s → 120s (최대 3회 재시도, 원본 포함 총 4회 시도).
     * 모든 시도 실패 시 로그를 남기고 다음 단계로 진행.
     */
    private void executeWithRetry(String stepName, Runnable step) {
        long delay = RETRY_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_RETRY + 1; attempt++) {
            try {
                step.run();
                return;
            } catch (Exception e) {
                log.error("[Batch] {} 실패 - {}/{}회", stepName, attempt, MAX_RETRY + 1, e);
                if (attempt <= MAX_RETRY) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    delay = Math.min((long) (delay * RETRY_MULTIPLIER), MAX_DELAY_MS);
                }
            }
        }
    }
}
