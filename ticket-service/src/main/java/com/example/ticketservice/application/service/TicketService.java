package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.usecase.TicketUseCase;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService implements TicketUseCase {

    private final TicketRepository ticketRepository;

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
        Page<Ticket> ticketPage = ticketRepository.findAllByUserId(userId, page, size);
        // dangling ticket (schedule 참조 끊김) 은 사용자에게 노출하지 않음
        List<TicketResponse> filtered = ticketPage.getContent().stream()
                .filter(ticket -> ticket.getSchedule() != null)
                .map(TicketResponse::from)
                .toList();
        Page<TicketResponse> resultPage = new PageImpl<>(
                filtered, ticketPage.getPageable(), ticketPage.getTotalElements());
        return PageResult.from(resultPage);
    }
}
