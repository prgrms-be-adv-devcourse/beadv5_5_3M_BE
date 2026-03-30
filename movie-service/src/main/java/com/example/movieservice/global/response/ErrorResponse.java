package com.example.movieservice.global.response;

public record ErrorResponse(
        String code,
        String message
) {}
