package com.example.aiservice.global.response;

public record ErrorResponse(
        String code,
        String message
) {}
