package com.example.streamingservice.application.dto;

import java.time.Instant;

public record ScheduleSummary(
		long scheduleId,
		String title,
		Instant startTime,
		Instant endTime,
		String imageUrl
) {
}