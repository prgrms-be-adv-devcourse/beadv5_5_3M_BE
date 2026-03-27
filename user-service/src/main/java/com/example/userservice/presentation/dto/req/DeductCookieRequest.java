package com.example.userservice.presentation.dto.req;

import java.util.UUID;

public record DeductCookieRequest(
        Long ticketId,
        Integer amount,
        UUID userId
) {
}
