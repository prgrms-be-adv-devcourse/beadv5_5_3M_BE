package com.example.ticketservice.application.event;

import java.util.List;
import java.util.UUID;

public record CartClosedEvent(
        Long scheduleId,
        String caseType,
        Integer seats,
        List<UUID> userIds
) {}