package com.example.movieservice.presentation.dto.request.review;

import jakarta.validation.constraints.*;

public record WriteReviewRequest(
        @NotNull @Positive Long movieId,
        @NotNull @Positive Long scheduleId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank String comment
) {}