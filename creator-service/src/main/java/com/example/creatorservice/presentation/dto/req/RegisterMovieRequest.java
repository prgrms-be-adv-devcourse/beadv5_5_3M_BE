package com.example.creatorservice.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegisterMovieRequest(
        @NotBlank @Size(max = 100) String title,
        String description,
        @NotNull @Positive Integer additionalCookie,
        List<Long> categoryIds
) {}