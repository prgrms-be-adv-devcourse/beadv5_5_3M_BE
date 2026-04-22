package com.example.streamingservice.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleConfirmedPayload(
		Long scheduleId,
		LocalDateTime startTime,
		LocalDateTime endTime,
		LocalDateTime ticketingTime,
		String title,
		Integer cookie,
		UUID creatorId,
		Long movieId,
		String imageUrl,
		Integer seats
) {
}