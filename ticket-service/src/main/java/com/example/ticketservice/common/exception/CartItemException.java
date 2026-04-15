package com.example.ticketservice.common.exception;

public class CartItemException extends RuntimeException {

    private final CartItemErrorCode errorCode;

    public CartItemException(CartItemErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CartItemErrorCode getErrorCode() {
        return errorCode;
    }
}