package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.request.TicketCreateRequest;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.event.TicketCancelledEvent;
import com.example.ticketservice.application.event.TicketReservedEvent;
import com.example.ticketservice.application.port.out.UserPort;
import com.example.ticketservice.application.usecase.TicketUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// TODO(Phase 8): reserveTicket → purchaseTicket 으로 리네임 및 로직 변경 예정
@Service
@RequiredArgsConstructor
public class TicketService implements TicketUseCase {

    private final TicketRepository ticketRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserPort userPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public TicketResponse reserveTicket(UUID userId, TicketCreateRequest request) {
        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(request.scheduleId()));

        if (schedule.getEndTime().isBefore(LocalDateTime.now().minusMinutes(10))) {
            throw ScheduleErrorCode.EXPIRED.of(request.scheduleId());
        }

        Ticket ticket = ticketRepository.findFirstAvailableBySchedule(schedule)
                .orElseThrow(() -> ScheduleErrorCode.FULL.of(request.scheduleId()));
        ticket.reserved(userId);
        ticketRepository.save(ticket);

        schedule.decreaseSeats();
        scheduleRepository.save(schedule);

        userPort.deductTicketFee(new DeductCookieRequest(ticket.getId(), schedule.getCookie(), userId));

        eventPublisher.publishEvent(
                new TicketReservedEvent(ticket.getId(), schedule.getId(), userId, schedule.getCookie()));

        return TicketResponse.from(ticket);
    }

    @Transactional
    @Override
    public void cancelTicket(UUID userId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));

        ticket.cancel();
        ticketRepository.save(ticket);

        ticket.getSchedule().increaseSeats();
        scheduleRepository.save(ticket.getSchedule());

        eventPublisher.publishEvent(
                new TicketCancelledEvent(ticket.getId(), ticket.getSchedule().getId(), userId, ticket.getSchedule().getCookie()));
    }

    @Transactional(readOnly = true)
    @Override
    public TicketResponse getTicket(UUID userId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<TicketResponse> getTicketsByUser(UUID userId, int page, int size) {
        Page<TicketResponse> ticketPage = ticketRepository.findAllByUserId(userId, page, size)
                .map(TicketResponse::from);
        return PageResult.from(ticketPage);
    }
}