package com.example.ticketservice.common.exception;

public class TicketException extends RuntimeException {

    private final TicketErrorCode errorCode;

    public TicketException(TicketErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TicketErrorCode getErrorCode() {
        return errorCode;
    }
}