package com.example.ticketservice.common.exception;

public class ScheduleException extends RuntimeException {

    private final ScheduleErrorCode errorCode;

    public ScheduleException(ScheduleErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ScheduleErrorCode getErrorCode() {
        return errorCode;
    }
}