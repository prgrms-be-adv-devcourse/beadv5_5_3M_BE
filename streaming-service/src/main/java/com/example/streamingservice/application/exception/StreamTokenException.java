package com.example.streamingservice.application.exception;

public class StreamTokenException extends RuntimeException {

	private final ErrorCode errorCode;

	private StreamTokenException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	private StreamTokenException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.defaultMessage(), cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public static StreamTokenException invalid() {
		return new StreamTokenException(ErrorCode.INVALID_TOKEN);
	}

	public static StreamTokenException invalid(Throwable cause) {
		return new StreamTokenException(ErrorCode.INVALID_TOKEN, cause);
	}

	public static StreamTokenException expired() {
		return new StreamTokenException(ErrorCode.SESSION_EXPIRED);
	}

	public static StreamTokenException expired(Throwable cause) {
		return new StreamTokenException(ErrorCode.SESSION_EXPIRED, cause);
	}
}