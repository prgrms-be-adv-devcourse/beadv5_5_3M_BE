package com.example.ticketservice.common.exception;

public class QueueException extends RuntimeException {

    private final QueueErrorCode errorCode;

    public QueueException(QueueErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public QueueErrorCode getErrorCode() {
        return errorCode;
    }
}