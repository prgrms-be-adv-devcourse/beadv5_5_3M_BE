package com.example.ticketservice.application.dto.response;

import java.util.UUID;

public record RefundCookieResponse(
        UUID userId,
        Long ticketId,
        Integer refundCookieAmount,
        boolean flag
) {}