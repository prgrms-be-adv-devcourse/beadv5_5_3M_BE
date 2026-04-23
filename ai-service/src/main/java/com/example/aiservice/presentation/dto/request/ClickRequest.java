package com.example.aiservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record ClickRequest(
        @NotNull Long logId
) {}
