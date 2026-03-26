package com.example.ticketservice.infrastructure.caching.scheduler;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.caching.dto.ReviewAuthorizationCache;
import com.example.ticketservice.infrastructure.messaging.dto.event.ReviewAuthorizationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAuthorizationScheduler {

    private static final String CACHE_KEY_PREFIX = "review:auth:pending:";
    private static final String TOPIC = "ticket.review.authorized";

    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;
    private final EventPublisherPort eventPublisherPort;
    private final JobLauncher jobLauncher;
    private final Job ticketConfirmJob;

    /**
     * 매시 50분 실행.
     *
     * 실행 순서 (정합성 보장):
     * 1. DB에서 다음 정각 시작 스케줄 조회
     * 2. Redis ZSet에서 스케줄별 리뷰 권한 캐시 수집 (아직 삭제 안 함)
     * 3. Spring Batch로 티켓 CONFIRMED 처리
     * 4. 배치 성공 시에만 → Redis ZSet 키 삭제 + Kafka 발행
     *    (배치 실패 시 Redis 캐시 유지 → 다음 재시도 가능)
     */
    @Scheduled(cron = "0 50 * * * *")
    public void run() {
        LocalDateTime nextHour = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS);
        List<Schedule> schedules = scheduleRepository.findAllByStartTimeBetween(nextHour, nextHour.plusMinutes(1));

        if (schedules.isEmpty()) {
            log.debug("다음 정각({})에 시작하는 스케줄 없음", nextHour);
            return;
        }

        // key: scheduleId string, value: 해당 스케줄의 ZSet 전체 멤버
        Map<String, Set<ReviewAuthorizationCache>> cacheMap = collectCaches(schedules);

        JobExecution execution = runBatchJob(schedules);

        if (execution != null && execution.getStatus() == BatchStatus.COMPLETED) {
            deleteCaches(schedules);
            publishReviewAuthorizations(cacheMap);
        } else {
            log.error("배치 실패 - Redis 캐시 유지 (재시도 가능). status: {}",
                    execution != null ? execution.getStatus() : "NULL");
        }
    }

    // 스케줄별 ZSet에서 전체 멤버 수집 (삭제 없이 읽기만)
    private Map<String, Set<ReviewAuthorizationCache>> collectCaches(List<Schedule> schedules) {
        return schedules.stream()
                .collect(Collectors.toMap(
                        s -> s.getId().toString(),
                        s -> cachePort.getZSetMembers(CACHE_KEY_PREFIX + s.getId(), ReviewAuthorizationCache.class)
                ));
    }

    // 배치 성공 후 스케줄별 ZSet 키 삭제
    private void deleteCaches(List<Schedule> schedules) {
        schedules.forEach(s -> cachePort.delete(CACHE_KEY_PREFIX + s.getId()));
        log.debug("Redis ZSet 삭제 완료 - count: {}", schedules.size());
    }

    // Spring Batch 동기 실행, JobExecution 반환
    private JobExecution runBatchJob(List<Schedule> schedules) {
        try {
            String scheduleIds = schedules.stream()
                    .map(s -> s.getId().toString())
                    .collect(Collectors.joining(","));

            JobParameters params = new JobParametersBuilder()
                    .addString("scheduleIds", scheduleIds)
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(ticketConfirmJob, params);
            log.info("배치 완료 - status: {}, scheduleIds: {}", execution.getStatus(), scheduleIds);
            return execution;
        } catch (Exception e) {
            log.error("배치 실행 중 예외 발생", e);
            return null;
        }
    }

    // Kafka 리뷰 권한 메시지 발행 (스케줄당 N건, 티켓별 발행)
    private void publishReviewAuthorizations(Map<String, Set<ReviewAuthorizationCache>> cacheMap) {
        long totalCount = cacheMap.values().stream().mapToLong(Set::size).sum();
        if (totalCount == 0) {
            log.debug("발행할 리뷰 권한 데이터 없음");
            return;
        }

        log.info("리뷰 권한 발행 시작 - count: {}", totalCount);

        cacheMap.values().stream()
                .flatMap(Set::stream)
                .forEach(cache -> {
                    ReviewAuthorizationMessage message = new ReviewAuthorizationMessage(
                            cache.ticketId(), cache.movieId(), cache.scheduleId(), cache.userId());
                    eventPublisherPort.publish(TOPIC, cache.ticketId().toString(), message);
                });

        log.info("리뷰 권한 발행 완료 - count: {}", totalCount);
    }
}