package com.example.ticketservice.infrastructure.batch;

import com.example.ticketservice.application.port.out.TicketCleanupBatchPort;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketCleanupBatchAdapter implements TicketCleanupBatchPort {

    private final TicketRepository ticketRepository;

    @Transactional
    @Override
    public void run(Long scheduleId) {
        int deleted = ticketRepository.deleteAllByScheduleIdAndStatus(scheduleId, TicketStatus.RESERVED);
        log.info("미결제 RESERVED 티켓 일괄 삭제 완료 - scheduleId={}, deleted={}", scheduleId, deleted);
    }
}