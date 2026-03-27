package com.example.rivewservice.infrastructure.messaging.dto.request;

import java.util.UUID;

//topic: ticket.review.authorized
public record ReviewAuthorizationMessage(
        Long ticketId,
        Long movieId,
        Long scheduleId,
        UUID userId
){
}
