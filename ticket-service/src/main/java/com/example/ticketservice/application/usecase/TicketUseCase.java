package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.common.model.PageResult;

import java.util.UUID;

public interface TicketUseCase {
    TicketResponse getTicket(UUID userId, Long ticketId);
    PageResult<TicketResponse> getTicketsByUser(UUID userId, int page, int size);
}