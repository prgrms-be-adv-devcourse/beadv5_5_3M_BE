package com.example.streamingservice.application.exception;

public class ScheduleException extends RuntimeException {

	private final ErrorCode errorCode;

	private ScheduleException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	private ScheduleException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.defaultMessage(), cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public static ScheduleException notFound() {
		return new ScheduleException(ErrorCode.SCHEDULE_NOT_FOUND);
	}

	public static ScheduleException streamLocationUnavailable() {
		return new ScheduleException(ErrorCode.STREAM_LOCATION_UNAVAILABLE);
	}

	public static ScheduleException streamLocationUnavailable(Throwable cause) {
		return new ScheduleException(ErrorCode.STREAM_LOCATION_UNAVAILABLE, cause);
	}
}