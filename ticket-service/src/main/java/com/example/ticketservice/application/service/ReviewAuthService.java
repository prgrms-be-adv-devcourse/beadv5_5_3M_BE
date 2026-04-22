package com.example.ticketservice.application.service;

import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.usecase.ReviewAuthUseCase;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
import com.example.ticketservice.infrastructure.messaging.dto.event.ReviewAuthorizationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAuthService implements ReviewAuthUseCase {

    private static final int PAGE_SIZE = 500;

    private final TicketRepository ticketRepository;
    private final EventPublisherPort eventPublisherPort;

    @Transactional(readOnly = true)
    @Override
    public void publishReviewAuth(Long scheduleId) {
        log.info("리뷰 권한 발행 시작 - scheduleId={}", scheduleId);
        int page = 0;
        int totalPublished = 0;

        Slice<Ticket> slice;
        do {
            slice = ticketRepository.findByScheduleIdAndStatus(scheduleId, TicketStatus.CONFIRMED, page, PAGE_SIZE);

            for (Ticket ticket : slice.getContent()) {
                eventPublisherPort.publish(
                        KafkaTopics.REVIEW_AUTHORIZED,
                        ticket.getId().toString(),
                        new ReviewAuthorizationMessage(
                                ticket.getId(),
                                ticket.getSchedule().getMovieId(),
                                scheduleId,
                                ticket.getUserId()
                        )
                );
            }

            totalPublished += slice.getNumberOfElements();
            page++;
        } while (slice.hasNext());

        log.info("리뷰 권한 발행 완료 - scheduleId={}, publishedCount={}", scheduleId, totalPublished);
    }
}