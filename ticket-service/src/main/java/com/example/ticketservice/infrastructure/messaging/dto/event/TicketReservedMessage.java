package com.example.ticketservice.infrastructure.messaging.dto.event;


import java.util.UUID;

//topic: ticket.reserved
//receiver: user-service, movie-service
public record TicketReservedMessage(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {
}
