package com.example.streamingservice.application.dto;

import java.util.UUID;

public record MovieLocation(
		long movieId,
		String videoUrl,
		Integer runningTime,
		String title,
		UUID creatorId
) {
}