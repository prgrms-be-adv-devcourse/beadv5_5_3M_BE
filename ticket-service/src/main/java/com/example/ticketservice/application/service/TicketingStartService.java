package com.example.ticketservice.application.service;

import com.example.ticketservice.application.event.TicketingStartedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.TicketCleanupBatchPort;
import com.example.ticketservice.application.usecase.TicketingStartUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketingStartService implements TicketingStartUseCase {

    static final String STOCK_KEY_PREFIX = "stock:schedule:";
    static final String PAYING_KEY_PREFIX = "paying:schedule:";

    private final ScheduleRepository scheduleRepository;
    private final TicketRepository ticketRepository;
    private final TicketCleanupBatchPort ticketCleanupBatchPort;
    private final CachePort cachePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        // 1. 미결제 RESERVED 티켓 일괄 삭제
        ticketCleanupBatchPort.run(scheduleId);

        // 2. 남은 재고 계산: 총 좌석 - 결제 완료(CONFIRMED) 수
        long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED);
        long remaining = schedule.getSeats() - confirmedCount;

        // 3. Redis stock / paying 카운터 설정 (공연 시작 10분 전 티켓팅 마감 TTL)
        Duration ttl = Duration.between(LocalDateTime.now(), schedule.getStartTime().minusMinutes(10));
        if (!ttl.isNegative() && !ttl.isZero()) {
            cachePort.setCounter(STOCK_KEY_PREFIX + scheduleId, remaining, ttl);
            cachePort.setCounter(PAYING_KEY_PREFIX + scheduleId, 0, ttl);
        }

        // 4. IN_PROGRESSING → TICKETING
        schedule.startTicketing();
        scheduleRepository.save(schedule);

        // 5. DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Kafka ticketing.started 발행
        eventPublisher.publishEvent(new TicketingStartedEvent(scheduleId));
        log.info("티켓팅 시작 - scheduleId={}, remaining={}", scheduleId, remaining);
    }
}