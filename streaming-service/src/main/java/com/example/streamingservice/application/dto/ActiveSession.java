package com.example.streamingservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSession(UUID sessionId, long scheduleId, Instant issuedAt) {
}