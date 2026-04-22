package com.example.ticketservice.application.dto.request;

import java.util.UUID;

public record RefundCookieRequest(
        Long ticketId,
        Integer amount,
        UUID userId
) {}