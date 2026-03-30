package com.example.userservice.presentation.dto.res;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
