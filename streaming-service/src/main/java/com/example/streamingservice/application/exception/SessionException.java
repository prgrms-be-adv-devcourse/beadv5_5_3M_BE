package com.example.streamingservice.application.exception;

public class SessionException extends RuntimeException {

	private final ErrorCode errorCode;

	private SessionException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	private SessionException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.defaultMessage(), cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public static SessionException noEntitlement() {
		return new SessionException(ErrorCode.NO_ENTITLEMENT);
	}

	public static SessionException windowClosed() {
		return new SessionException(ErrorCode.WINDOW_CLOSED);
	}

	public static SessionException sessionExpired() {
		return new SessionException(ErrorCode.SESSION_EXPIRED);
	}

	public static SessionException sessionMismatch() {
		return new SessionException(ErrorCode.SESSION_MISMATCH);
	}

	public static SessionException invalidFileName() {
		return new SessionException(ErrorCode.INVALID_FILE_NAME);
	}

	public static SessionException segmentNotFound() {
		return new SessionException(ErrorCode.SEGMENT_NOT_FOUND);
	}

	public static SessionException segmentNotFound(Throwable cause) {
		return new SessionException(ErrorCode.SEGMENT_NOT_FOUND, cause);
	}
}