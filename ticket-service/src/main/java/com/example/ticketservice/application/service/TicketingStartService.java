package com.example.ticketservice.application.service;

import com.example.ticketservice.application.event.TicketingStartedEvent;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketingStartService implements TicketingStartUseCase {

    private final ScheduleRepository scheduleRepository;
    private final TicketRepository ticketRepository;
    private final TicketCleanupBatchPort ticketCleanupBatchPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        // 1. 미결제 RESERVED 티켓 일괄 삭제 — 실패 시 TICKETING 전이 중단
        try {
            ticketCleanupBatchPort.run(scheduleId);
        } catch (Exception e) {
            throw new RuntimeException("미결제 RESERVED 티켓 삭제 실패 - scheduleId=" + scheduleId
                    + ", TICKETING 전이를 중단합니다.", e);
        }

        // 2. 남은 재고 계산: 총 좌석 - 결제 완료(CONFIRMED) 수
        long confirmedCount = ticketRepository.countByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED);
        long remaining = schedule.getSeats() - confirmedCount;

        // 3. IN_PROGRESSING → TICKETING
        schedule.startTicketing();
        scheduleRepository.save(schedule);

        // 4. DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Redis 키 설정 + Kafka 발행
        //    Redis 설정을 AFTER_COMMIT으로 이동: DB 롤백 시 Redis 키 잔류 방지
        eventPublisher.publishEvent(new TicketingStartedEvent(
                scheduleId, remaining, schedule.getSeats(), schedule.getCookie(), schedule.getStartTime()));
        log.info("티켓팅 시작 - scheduleId={}, remaining={}", scheduleId, remaining);
    }
}