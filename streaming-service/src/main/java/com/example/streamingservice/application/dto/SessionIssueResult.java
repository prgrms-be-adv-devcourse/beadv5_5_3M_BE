package com.example.streamingservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionIssueResult(
		String sessionToken,
		UUID sessionId,
		String manifestUrl,
		String wsEndpoint,
		Instant expiresAt,
		ScheduleSummary schedule
) {
}