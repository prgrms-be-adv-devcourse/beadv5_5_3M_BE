package com.example.aiservice.presentation.controller;

import com.example.aiservice.application.batch.BatchPreprocessingService;
import com.example.aiservice.application.batch.BatchScheduler;
import com.example.aiservice.application.batch.KMeansClusteringService;
import com.example.aiservice.application.batch.RecommendationCalculationService;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/internal/batch")
@RequiredArgsConstructor
public class BatchTestController {

    private final BatchScheduler batchScheduler;
    private final BatchPreprocessingService batchPreprocessingService;
    private final KMeansClusteringService kMeansClusteringService;
    private final RecommendationCalculationService recommendationCalculationService;
    private final RecommendedLogRepository recommendedLogRepository;

    /** 전체 배치 제출 (4단계 순차 실행 — 스케줄러와 동일) */
    @PostMapping("/submit-daily")
    public ResponseEntity<String> submitDaily() {
        log.info("[BatchTest] submitDailyBatch 수동 트리거");
        batchScheduler.submitDailyBatch();
        return ResponseEntity.ok("submitDailyBatch 완료");
    }

    /** 배치 결과 수령 (02:00 스케줄러와 동일) */
    @PostMapping("/receive")
    public ResponseEntity<String> receive() {
        log.info("[BatchTest] receiveDailyBatchResults 수동 트리거");
        batchScheduler.receiveDailyBatchResults();
        return ResponseEntity.ok("receiveDailyBatchResults 완료");
    }

    /** recommended_log 정리 */
    @PostMapping("/cleanup")
    public ResponseEntity<String> cleanup() {
        log.info("[BatchTest] cleanupRecommendedLog 수동 트리거");
        batchPreprocessingService.cleanupRecommendedLog();
        return ResponseEntity.ok("cleanupRecommendedLog 완료");
    }

    /**
     * epsilon 갱신.
     * body: UUID 목록 (비어 있으면 어제 활성 유저 자동 조회)
     */
    @PostMapping("/epsilon")
    public ResponseEntity<String> epsilon(@RequestBody(required = false) List<UUID> userIds) {
        List<UUID> targets = resolveTargets(userIds);
        log.info("[BatchTest] updateEpsilon 수동 트리거 - 유저: {}명", targets.size());
        batchPreprocessingService.updateEpsilon(targets);
        return ResponseEntity.ok("updateEpsilon 완료 - 처리 유저: " + targets.size() + "명");
    }

    /**
     * K-Means 재계산.
     * body: UUID 목록 (비어 있으면 어제 활성 유저 자동 조회)
     */
    @PostMapping("/kmeans")
    public ResponseEntity<String> kmeans(@RequestBody(required = false) List<UUID> userIds) {
        List<UUID> targets = resolveTargets(userIds);
        log.info("[BatchTest] recalculateClusters 수동 트리거 - 유저: {}명", targets.size());
        kMeansClusteringService.recalculateClusters(targets);
        return ResponseEntity.ok("recalculateClusters 완료 - 처리 유저: " + targets.size() + "명");
    }

    /**
     * 추천 후보 선정 및 OpenAI 배치 제출.
     * body: UUID 목록 (비어 있으면 어제 활성 유저 자동 조회)
     */
    @PostMapping("/calculate")
    public ResponseEntity<String> calculate(@RequestBody(required = false) List<UUID> userIds) {
        List<UUID> targets = resolveTargets(userIds);
        log.info("[BatchTest] submitBatchForAllUsers 수동 트리거 - 유저: {}명", targets.size());
        recommendationCalculationService.submitBatchForAllUsers(targets);
        return ResponseEntity.ok("submitBatchForAllUsers 완료 - 처리 유저: " + targets.size() + "명");
    }

    private List<UUID> resolveTargets(List<UUID> userIds) {
        if (userIds != null && !userIds.isEmpty()) {
            return userIds;
        }
        return recommendedLogRepository.findActiveUserIds(LocalDate.now().minusDays(1));
    }
}
