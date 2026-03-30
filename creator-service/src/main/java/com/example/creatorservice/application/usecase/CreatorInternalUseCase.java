package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.PayoutAccountResponse;

import java.util.UUID;

public interface CreatorInternalUseCase {
    PayoutAccountResponse getPayoutAccount(UUID creatorId);
}
