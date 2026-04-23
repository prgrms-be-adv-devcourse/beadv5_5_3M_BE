package com.example.streamingservice.application.dto;

import java.util.UUID;

public record SendChatCommand(UUID userId, String nickname, long scheduleId, String content) {
}