package com.example.streamingservice.application.exception;

public class ChatException extends RuntimeException {

	private final ErrorCode errorCode;

	private ChatException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public static ChatException rateLimited() {
		return new ChatException(ErrorCode.CHAT_RATE_LIMITED);
	}

	public static ChatException tooLong() {
		return new ChatException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
	}
}