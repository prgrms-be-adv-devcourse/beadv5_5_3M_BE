package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.response.TicketResponse;

import java.util.UUID;

public interface SelfPaymentUseCase {

    TicketResponse pay(UUID userId, Long ticketId);
}