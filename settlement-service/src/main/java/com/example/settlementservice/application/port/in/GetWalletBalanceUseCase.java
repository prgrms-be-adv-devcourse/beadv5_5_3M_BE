package com.example.settlementservice.application.port.in;

import com.example.settlementservice.presentation.web.dto.WalletResponse;

import java.util.UUID;

public interface GetWalletBalanceUseCase {
    WalletResponse getBalance(UUID creatorId);
}