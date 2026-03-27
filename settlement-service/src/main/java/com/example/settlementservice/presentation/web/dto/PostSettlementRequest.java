package com.example.settlementservice.presentation.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PostSettlementRequest(

        @NotNull(message = "Request amount is required")
        @Positive(message = "Request amount must be positive")
        Long requestAmount
) {}