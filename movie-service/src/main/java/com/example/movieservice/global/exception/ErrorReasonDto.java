package com.example.movieservice.global.exception;

import org.springframework.http.HttpStatus;

public record ErrorReasonDto(
        HttpStatus httpStatus,
        String code,
        String message
) {
}