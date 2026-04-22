package com.example.ticketservice.infrastructure.messaging.dto.event;

import java.util.UUID;

//topic: ticket.refunded
//receiver: user-service (쿠키 환불), notification-service
public record TicketRefundedMessage(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {}