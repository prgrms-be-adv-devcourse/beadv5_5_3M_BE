package com.example.userservice.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record JoinRequest(
        @NotBlank
        String email,

        @NotBlank
        String password,

        @NotBlank
        String nickname
) {
}
