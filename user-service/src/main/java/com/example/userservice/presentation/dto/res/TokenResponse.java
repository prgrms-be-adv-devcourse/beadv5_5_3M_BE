package com.example.userservice.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
        @Schema(description = "액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "리프레시 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken
) {
}
