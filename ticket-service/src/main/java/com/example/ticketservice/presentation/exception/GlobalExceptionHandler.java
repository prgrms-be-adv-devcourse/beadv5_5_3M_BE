package com.example.ticketservice.presentation.exception;

import com.example.ticketservice.common.exception.ScheduleException;
import com.example.ticketservice.common.exception.TicketException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<Map<String, Object>> handleTicketException(TicketException e) {
        return buildResponse(e.getErrorCode().getStatus().value(), e.getMessage());
    }

    @ExceptionHandler(ScheduleException.class)
    public ResponseEntity<Map<String, Object>> handleScheduleException(ScheduleException e) {
        return buildResponse(e.getErrorCode().getStatus().value(), e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}