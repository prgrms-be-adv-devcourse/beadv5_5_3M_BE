package com.example.rivewservice.presentation;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.rivewservice.common.exception.ReviewException;
import com.example.rivewservice.common.exception.UserException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<Map<String, Object>> handleUserException(UserException e) {
        return buildResponse(e.getStatus().value(), e.getMessage());
    }

    @ExceptionHandler(ReviewException.class)
    public ResponseEntity<Map<String, Object>> handleReviewException(ReviewException e) {
        return buildResponse(e.getStatus().value(), e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}