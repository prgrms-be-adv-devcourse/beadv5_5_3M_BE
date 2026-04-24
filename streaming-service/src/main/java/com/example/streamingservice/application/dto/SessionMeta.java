package com.example.streamingservice.application.dto;

import java.util.UUID;

public record SessionMeta(UUID userId, long scheduleId) {
}