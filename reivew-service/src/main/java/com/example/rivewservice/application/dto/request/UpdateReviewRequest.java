package com.example.rivewservice.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateReviewRequest(
        @NotBlank String comment,
        @NotNull @Min(1) @Max(5) Integer rating
) {
}