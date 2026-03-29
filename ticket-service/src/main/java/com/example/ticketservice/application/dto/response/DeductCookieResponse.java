package com.example.ticketservice.application.dto.response;

import java.util.UUID;

public record DeductCookieResponse(
        UUID userId,
        Long ticketId,
        Integer deductCookieAmount,
        boolean flag
) {
}