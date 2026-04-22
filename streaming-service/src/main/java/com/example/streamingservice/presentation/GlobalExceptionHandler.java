package com.example.streamingservice.presentation;

import com.example.streamingservice.application.exception.ChatException;
import com.example.streamingservice.application.exception.ErrorCode;
import com.example.streamingservice.application.exception.ScheduleException;
import com.example.streamingservice.application.exception.SessionException;
import com.example.streamingservice.application.exception.StreamTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(SessionException.class)
	public ResponseEntity<ErrorResponse> handleSession(SessionException e) {
		return build(e.errorCode());
	}

	@ExceptionHandler(ScheduleException.class)
	public ResponseEntity<ErrorResponse> handleSchedule(ScheduleException e) {
		return build(e.errorCode());
	}

	@ExceptionHandler(StreamTokenException.class)
	public ResponseEntity<ErrorResponse> handleStreamToken(StreamTokenException e) {
		return build(e.errorCode());
	}

	@ExceptionHandler(ChatException.class)
	public ResponseEntity<ErrorResponse> handleChat(ChatException e) {
		return build(e.errorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorResponse("VALIDATION_ERROR", e.getMessage(), Instant.now()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("unhandled exception", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ErrorResponse("INTERNAL_ERROR", "서버 오류", Instant.now()));
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode code) {
		return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(code));
	}
}