package com.example.userservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpMethod;

@Schema(description = "권한 확인 요청")
public record AuthorizationRequest(
        @Schema(description = "HTTP 메서드", example = "GET", requiredMode = Schema.RequiredMode.REQUIRED)
        HttpMethod httpMethod,

        @Schema(description = "요청 경로", example = "/api/users/me", requiredMode = Schema.RequiredMode.REQUIRED)
        String requestPath
) {
}
