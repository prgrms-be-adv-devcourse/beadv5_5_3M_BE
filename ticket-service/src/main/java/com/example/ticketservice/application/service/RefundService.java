package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.request.RefundCookieRequest;
import com.example.ticketservice.application.event.TicketRefundedEvent;
import com.example.ticketservice.application.port.out.UserPort;
import com.example.ticketservice.application.usecase.RefundUseCase;
import com.example.ticketservice.common.exception.TicketErrorCode;
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
    private final UserPort userPort;
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

        // 직접 환불: /internal/users/refund/cookie
        userPort.refundCookie(
                new RefundCookieRequest(ticketId, ticket.getSchedule().getCookie(), userId));

        Long scheduleId = ticket.getSchedule().getId();
        Integer cookie = ticket.getSchedule().getCookie();

        // CONFIRMED 티켓 DELETE
        ticketRepository.delete(ticket);

        // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서:
        // - stock INCR (티켓팅 중이면)
        // - queueAutoProcessService.checkAndProcess()
        // - Kafka ticket.refunded 발행
        eventPublisher.publishEvent(new TicketRefundedEvent(ticketId, scheduleId, userId, cookie));
        log.info("환불 완료 - ticketId={}, userId={}", ticketId, userId);
    }
}