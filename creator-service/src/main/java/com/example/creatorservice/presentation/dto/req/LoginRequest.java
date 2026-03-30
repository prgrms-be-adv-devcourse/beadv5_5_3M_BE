package com.example.creatorservice.presentation.dto.req;

public record LoginRequest(
        String email,
        String password
) {
}
