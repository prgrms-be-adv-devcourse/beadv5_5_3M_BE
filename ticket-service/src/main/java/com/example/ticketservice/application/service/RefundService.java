package com.example.ticketservice.application.service;

import com.example.ticketservice.application.event.TicketRefundedEvent;
import com.example.ticketservice.application.usecase.RefundUseCase;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService implements RefundUseCase {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void refund(UUID userId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw TicketErrorCode.NOT_YOUR_TICKET.of(ticketId);
        }

        if (ticket.getStatus() != TicketStatus.CONFIRMED) {
            throw TicketErrorCode.NOT_CONFIRMED.of(ticketId);
        }

        // LOBBY 진입 시점부터 환불 차단 (IN_PROGRESSING / TICKETING 에서만 허용)
        ScheduleStatus status = ticket.getSchedule().getStatus();
        if (status != ScheduleStatus.IN_PROGRESSING && status != ScheduleStatus.TICKETING) {
            throw TicketErrorCode.REFUND_DEADLINE_PASSED.of(ticketId);
        }

        Long scheduleId = ticket.getSchedule().getId();
        Integer cookie = ticket.getSchedule().getCookie();

        // CONFIRMED 티켓 DELETE
        ticketRepository.delete(ticket);

        // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서:
        // - 쿠키 환불 HTTP 호출 (DB 커밋 후 실행 → 이중 환불 방지)
        // - stock INCR (티켓팅 중이면) + queue.drain 발행
        // - Kafka ticket.refunded 발행
        eventPublisher.publishEvent(new TicketRefundedEvent(ticketId, scheduleId, userId, cookie));
        log.info("환불 완료 - ticketId={}, userId={}", ticketId, userId);
    }
}