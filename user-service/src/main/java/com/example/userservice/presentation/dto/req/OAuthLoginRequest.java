package com.example.userservice.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank
        String code
) {}
