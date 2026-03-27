package com.example.rivewservice.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class UserException extends RuntimeException {

    private final HttpStatus status;

    UserException(UserErrorCode errorCode, String message) {
        super(message);
        this.status = errorCode.getStatus();
    }
}