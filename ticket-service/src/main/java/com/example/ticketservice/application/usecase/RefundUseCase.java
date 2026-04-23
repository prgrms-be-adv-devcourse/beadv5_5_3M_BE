package com.example.ticketservice.application.usecase;

import java.util.UUID;

public interface RefundUseCase {

    void refund(UUID userId, Long ticketId);
}