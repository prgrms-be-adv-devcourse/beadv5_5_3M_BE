package com.example.creatorservice.presentation.dto.res;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
