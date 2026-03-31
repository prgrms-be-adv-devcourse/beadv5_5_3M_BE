package com.example.settlementservice.application.service;

import com.example.settlementservice.application.port.in.GetWalletBalanceUseCase;
import com.example.settlementservice.application.port.out.WalletRepository;
import com.example.settlementservice.presentation.web.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletQueryService implements GetWalletBalanceUseCase {

    private final WalletRepository walletRepository;

    @Override
    public WalletResponse getBalance(UUID creatorId) {
        return walletRepository.findByCreatorId(creatorId)
                .map(WalletResponse::from)
                .orElse(new WalletResponse(creatorId, 0L));
    }
}