package com.example.creatorservice.presentation.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateMovieDetailRequest(
        @NotBlank @Size(max = 100) String title,
        String description,
        @NotNull @Positive Integer additionalCookie,
        @NotNull @Size(min = 1, message = "카테고리를 1개 이상 선택해야 합니다") List<Long> categoryIds
) {}