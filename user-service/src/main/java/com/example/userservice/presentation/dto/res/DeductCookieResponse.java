package com.example.userservice.presentation.dto.res;

import java.util.UUID;

public record DeductCookieResponse(
        UUID userId,
        Long ticketId,
        Integer deductCookieAmount,
        boolean flag
) {
}
