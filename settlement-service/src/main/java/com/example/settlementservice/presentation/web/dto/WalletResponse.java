package com.example.settlementservice.presentation.web.dto;

import com.example.settlementservice.domain.wallet.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "지갑 잔액 응답")
public record WalletResponse(
        @Schema(description = "크리에이터 ID") UUID creatorId,
        @Schema(description = "현재 잔액 (원 단위)") Long balance
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getCreatorId(), wallet.getBalance());
    }
}