package com.example.userservice.application.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("REFRESH_TOKEN_EXPIRED");
    }
}
