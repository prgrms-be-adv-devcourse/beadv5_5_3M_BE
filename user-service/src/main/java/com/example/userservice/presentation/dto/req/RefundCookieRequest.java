package com.example.userservice.presentation.dto.req;

import java.util.UUID;

public record RefundCookieRequest(
        Long ticketId,
        Integer amount,
        UUID userId
) {}
