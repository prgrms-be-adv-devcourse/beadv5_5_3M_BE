package com.example.ticketservice.infrastructure.messaging.dto.event;

import java.util.UUID;

//topic: ticket.paid
//receiver: user-service (쿠키 차감 확정), notification-service
public record TicketPaidMessage(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {}
