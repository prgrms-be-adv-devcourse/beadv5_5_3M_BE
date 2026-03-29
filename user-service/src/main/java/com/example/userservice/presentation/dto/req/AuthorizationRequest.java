package com.example.userservice.presentation.dto.req;

import org.springframework.http.HttpMethod;

public record AuthorizationRequest(
        HttpMethod httpMethod,
        String requestPath
) {
}
