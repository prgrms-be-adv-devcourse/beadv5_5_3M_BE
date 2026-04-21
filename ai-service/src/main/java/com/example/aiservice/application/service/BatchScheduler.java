package com.example.aiservice.application.service;

import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import com.example.aiservice.domain.repository.RecommendedLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    @Value("${batch.diversity-window-min-days}")
    private int diversityWindowMinDays;

    @Value("${batch.diversity-window-max-days}")
    private int diversityWindowMaxDays;

    @Value("${batch.ctr-window-days}")
    private int ctrWindowDays;

    private static final int DAILY_RECOMMENDATION_COUNT = 15;

    private final MovieEmbeddedRepository movieEmbeddedRepository;
    private final RecommendedLogRepository recommendedLogRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyBatch() {
        cleanupRecommendedLog();
        // 3번: epsilon 갱신
        // 2번: K-Means 클러스터 재계산
        // 4번: 추천 계산
    }

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
}
