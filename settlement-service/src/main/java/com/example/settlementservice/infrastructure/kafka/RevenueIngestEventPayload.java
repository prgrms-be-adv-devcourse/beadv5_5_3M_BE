package com.example.settlementservice.infrastructure.kafka;

import java.util.UUID;

// topic: ticket.provide
public record RevenueIngestEventPayload(
        UUID creatorId,
        Long ticketId,
        Long scheduleId,
        Integer cookieAmount
) {}