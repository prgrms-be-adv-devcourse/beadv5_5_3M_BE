package com.example.streamingservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
		UUID messageId,
		UUID userId,
		String nickname,
		String content,
		Instant at
) {
}