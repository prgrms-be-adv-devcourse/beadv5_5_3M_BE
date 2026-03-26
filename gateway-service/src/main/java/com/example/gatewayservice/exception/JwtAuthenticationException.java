package com.example.gatewayservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class JwtAuthenticationException extends RuntimeException {

    private final HttpStatus status;

    public JwtAuthenticationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

}
