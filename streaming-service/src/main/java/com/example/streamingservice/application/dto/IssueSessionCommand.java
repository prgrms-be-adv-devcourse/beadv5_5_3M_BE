package com.example.streamingservice.application.dto;

import java.util.UUID;

public record IssueSessionCommand(UUID userId, long scheduleId) {
}