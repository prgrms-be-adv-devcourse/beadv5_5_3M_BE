package com.example.ticketservice.infrastructure.messaging.dto.event;

import java.util.UUID;


//topic: ticket.provide
//receiver: settlement-service
public record DailyTicketFeeProvideMessage(
        UUID creatorId,
        Long ticketId,
        Long scheduleId,
        Integer cookieAmount
) {
}
