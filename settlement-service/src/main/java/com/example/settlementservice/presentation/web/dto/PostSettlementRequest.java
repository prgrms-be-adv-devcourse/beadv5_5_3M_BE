package com.example.settlementservice.presentation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "정산 신청 요청")
public record PostSettlementRequest(

        @Schema(description = "정산 신청 금액 (원 단위, 양수)", example = "150000")
        @NotNull(message = "Request amount is required")
        @Positive(message = "Request amount must be positive")
        Long requestAmount
) {}