package com.example.userservice.presentation.dto.res;

import java.util.UUID;

public record RefundCookieResponse(
        UUID userId,
        Long ticketId,
        Integer refundCookieAmount,
        boolean flag
) {}
