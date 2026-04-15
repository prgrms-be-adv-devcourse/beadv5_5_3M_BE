package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.usecase.ReviewAuthUseCase;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.messaging.dto.event.ReviewAuthorizationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAuthService implements ReviewAuthUseCase {

    public static final String REVIEW_AUTH_TOPIC = "ticket.review.authorized";

    private final TicketRepository ticketRepository;
    private final EventPublisherPort eventPublisherPort;

    @Transactional(readOnly = true)
    @Override
    public void publishReviewAuth(Long scheduleId) {
        List<Ticket> tickets = ticketRepository.findAllByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED);
        log.info("리뷰 권한 발행 시작 - scheduleId={}, confirmedCount={}", scheduleId, tickets.size());

        for (Ticket ticket : tickets) {
            eventPublisherPort.publish(
                    REVIEW_AUTH_TOPIC,
                    ticket.getId().toString(),
                    new ReviewAuthorizationMessage(
                            ticket.getId(),
                            ticket.getSchedule().getMovieId(),
                            scheduleId,
                            ticket.getUserId()
                    )
            );
        }

        log.info("리뷰 권한 발행 완료 - scheduleId={}, publishedCount={}", scheduleId, tickets.size());
    }
}