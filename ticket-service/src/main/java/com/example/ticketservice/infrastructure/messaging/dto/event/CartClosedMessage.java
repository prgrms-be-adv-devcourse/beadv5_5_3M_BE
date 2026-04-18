package com.example.ticketservice.infrastructure.messaging.dto.event;

import java.util.List;
import java.util.UUID;

public record CartClosedMessage(
        Long scheduleId,
        String caseType,
        Integer seats,
        List<UUID> userIds
) {}