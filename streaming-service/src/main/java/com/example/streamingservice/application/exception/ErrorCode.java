package com.example.streamingservice.application.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	NO_ENTITLEMENT(HttpStatus.FORBIDDEN, "해당 스케줄에 대한 입장 권한이 없습니다."),
	SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "스케줄을 찾을 수 없습니다."),
	WINDOW_CLOSED(HttpStatus.GONE, "현재 시각은 입장 가능한 시간대가 아닙니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
	SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 입장해 주세요."),
	SESSION_MISMATCH(HttpStatus.CONFLICT, "세션이 일치하지 않거나 밀려났습니다."),
	INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "HLS 파일명이 올바르지 않습니다."),
	SEGMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 세그먼트를 찾을 수 없습니다."),
	STREAM_LOCATION_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "영상 위치를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."),
	CHAT_RATE_LIMITED(HttpStatus.BAD_REQUEST, "메시지 전송 속도 제한을 초과했습니다."),
	CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "메시지 길이가 허용 범위를 초과했습니다.");

	private final HttpStatus httpStatus;
	private final String defaultMessage;

	ErrorCode(HttpStatus httpStatus, String defaultMessage) {
		this.httpStatus = httpStatus;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus httpStatus() {
		return httpStatus;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}