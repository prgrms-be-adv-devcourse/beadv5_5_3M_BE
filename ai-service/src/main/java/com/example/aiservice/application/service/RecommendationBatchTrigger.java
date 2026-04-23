package com.example.aiservice.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class RecommendationBatchTrigger {

    // TODO: 7번(배치) 구현 시 단일 유저 추천 재계산 로직으로 채울 것
    @Async
    public void trigger(UUID userId) {
        log.info("[Recommendation] 백그라운드 재계산 예약 - userId: {}", userId);
    }
}
