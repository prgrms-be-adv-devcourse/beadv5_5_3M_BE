package com.example.ticketservice.infrastructure.messaging.dto.event;

// topic: ticketing.started
// receiver: notification-service
public record TicketingStartedMessage(Long scheduleId) {}