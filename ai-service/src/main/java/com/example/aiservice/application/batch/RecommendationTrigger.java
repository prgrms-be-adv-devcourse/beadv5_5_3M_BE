package com.example.aiservice.application.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationTrigger {

    private final RecommendationCalculationService recommendationCalculationService;

    /**
     * 추천 결과가 7개 이하일 때 백그라운드에서 단일 유저 재계산을 수행한다.
     * LLM 없이 벡터 기반 fallback으로 즉시 처리 (Batch API 불필요).
     */
    @Async
    public void trigger(UUID userId) {
        log.info("[Recommendation] 백그라운드 재계산 시작 - userId: {}", userId);
        try {
            recommendationCalculationService.calculateWithVectorFallback(userId);
            log.info("[Recommendation] 백그라운드 재계산 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("[Recommendation] 백그라운드 재계산 실패 - userId: {}", userId, e);
        }
    }
}
