package com.example.creatorservice.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RegisterCategoryRequest(
        @NotBlank String name
) {
}