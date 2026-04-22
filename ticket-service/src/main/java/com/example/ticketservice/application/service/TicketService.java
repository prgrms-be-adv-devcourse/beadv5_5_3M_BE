package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.usecase.TicketUseCase;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Page<TicketResponse> ticketPage = ticketRepository.findAllByUserId(userId, page, size)
                .map(TicketResponse::from);
        return PageResult.from(ticketPage);
    }
}
