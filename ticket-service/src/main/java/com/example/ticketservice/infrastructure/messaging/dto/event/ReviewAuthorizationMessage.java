package com.example.ticketservice.infrastructure.messaging.dto.event;

import java.util.UUID;


//topic: ticket.review.authorized
//receiver: review-service
public record ReviewAuthorizationMessage(
        Long ticketId,
        Long movieId,
        Long scheduleId,
        UUID userId
){
}
