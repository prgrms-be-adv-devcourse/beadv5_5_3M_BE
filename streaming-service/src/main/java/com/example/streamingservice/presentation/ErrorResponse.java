package com.example.streamingservice.presentation;

import com.example.streamingservice.application.exception.ErrorCode;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {

	public static ErrorResponse of(ErrorCode code) {
		return new ErrorResponse(code.name(), code.defaultMessage(), Instant.now());
	}
}