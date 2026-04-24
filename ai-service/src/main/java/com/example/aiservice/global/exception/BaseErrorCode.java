package com.example.aiservice.global.exception;

public interface BaseErrorCode {

    ErrorReasonDto getReason();

    ErrorReasonDto getReasonHttpStatus();
}
