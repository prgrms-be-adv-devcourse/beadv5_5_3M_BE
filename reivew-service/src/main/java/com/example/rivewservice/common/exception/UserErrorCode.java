package com.example.rivewservice.common.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다: %s"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 유저입니다: %s"),
    USER_DELETED(HttpStatus.GONE, "삭제된 유저입니다: %s");

    private final HttpStatus status;
    private final String messageTemplate;

    UserErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public UserException of(UUID userId) {
        return new UserException(this, String.format(messageTemplate, userId));
    }

    public String message(Object arg) {
        return String.format(messageTemplate, arg);
    }
}