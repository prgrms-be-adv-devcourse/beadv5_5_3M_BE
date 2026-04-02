package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.port.out.TicketConfirmBatchPort;
import com.example.ticketservice.application.usecase.ConfirmScheduledTicketsUseCase;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.caching.dto.ReviewAuthorizationCache;
import com.example.ticketservice.infrastructure.messaging.dto.event.ReviewAuthorizationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmScheduledTicketsService implements ConfirmScheduledTicketsUseCase {

    private static final String CACHE_KEY_PREFIX = "review:auth:pending:";
    private static final String TOPIC = "ticket.review.authorized";

    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;
    private final EventPublisherPort eventPublisherPort;
    private final TicketConfirmBatchPort ticketConfirmBatchPort;

    /**
     * 매시 50분 스케줄러에 의해 호출.
     * 실행 순서 (정합성 보장):
     * 1. DB에서 다음 정각 시작 스케줄 조회
     * 2. Redis ZSet에서 스케줄별 리뷰 권한 캐시 수집 (아직 삭제 안 함)
     * 3. Spring Batch로 티켓 CONFIRMED 처리
     * 4. 배치 성공 시에만 → Redis ZSet 키 삭제 + Kafka 발행
     *    (배치 실패 시 Redis 캐시 유지 → 다음 재시도 가능)
     */
    @Override
    public void confirm() {
        LocalDateTime nextHour = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS);
        List<Schedule> schedules = scheduleRepository.findAllByStartTimeBetween(nextHour, nextHour.plusMinutes(1));

        log.info("-----batch 시작-----");
        if (schedules.isEmpty()) {
            log.info("다음 정각({})에 시작하는 스케줄 없음", nextHour);
            return;
        }

        Map<String, Set<ReviewAuthorizationCache>> cacheMap = collectCaches(schedules);

        List<Long> scheduleIds = schedules.stream().map(Schedule::getId).toList();
        boolean success = ticketConfirmBatchPort.run(scheduleIds);

        if (success) {
            deleteCaches(schedules);
            publishReviewAuthorizations(cacheMap);
        } else {
            log.error("배치 실패 - Redis 캐시 유지 (재시도 가능)");
        }
    }

    private Map<String, Set<ReviewAuthorizationCache>> collectCaches(List<Schedule> schedules) {
        return schedules.stream()
                .collect(Collectors.toMap(
                        s -> s.getId().toString(),
                        s -> cachePort.getZSetMembers(CACHE_KEY_PREFIX + s.getId(), ReviewAuthorizationCache.class)
                ));
    }

    private void deleteCaches(List<Schedule> schedules) {
        schedules.forEach(s -> cachePort.delete(CACHE_KEY_PREFIX + s.getId()));
        log.info("Redis ZSet 삭제 완료 - count: {}", schedules.size());
    }

    private void publishReviewAuthorizations(Map<String, Set<ReviewAuthorizationCache>> cacheMap) {
        long totalCount = cacheMap.values().stream().mapToLong(Set::size).sum();
        if (totalCount == 0) {
            log.info("발행할 리뷰 권한 데이터 없음");
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