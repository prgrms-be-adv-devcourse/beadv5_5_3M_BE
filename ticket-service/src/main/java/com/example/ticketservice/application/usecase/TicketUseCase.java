package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.request.TicketCreateRequest;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.common.model.PageResult;

import java.util.UUID;

public interface TicketUseCase {
    TicketResponse reserveTicket(UUID userId, TicketCreateRequest request);
    void cancelTicket(UUID userId, Long ticketId);
    TicketResponse getTicket(UUID userId, Long ticketId);
    PageResult<TicketResponse> getTicketsByUser(UUID userId, int page, int size);
}
