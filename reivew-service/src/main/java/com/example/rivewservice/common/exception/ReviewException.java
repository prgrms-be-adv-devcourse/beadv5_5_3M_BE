package com.example.rivewservice.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ReviewException extends RuntimeException {

    private final HttpStatus status;

    ReviewException(ReviewErrorCode errorCode, String message) {
        super(message);
        this.status = errorCode.getStatus();
    }
}