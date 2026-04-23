package com.example.aiservice.global.exception;

import org.springframework.http.HttpStatus;

public record ErrorReasonDto(
        HttpStatus httpStatus,
        String code,
        String message
) {
}
