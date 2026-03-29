package com.example.ticketservice.application.dto.request;

import java.util.UUID;

public record DeductCookieRequest(
        Long ticketId,
        Integer amount,
        UUID userId
) {
}
