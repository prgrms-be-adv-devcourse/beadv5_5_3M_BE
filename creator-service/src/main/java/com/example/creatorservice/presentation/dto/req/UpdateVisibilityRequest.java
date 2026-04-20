package com.example.creatorservice.presentation.dto.req;

import com.example.creatorservice.domain.model.Movie.Visibility;
import jakarta.validation.constraints.NotNull;

public record UpdateVisibilityRequest(
        @NotNull Visibility visibility
) {}