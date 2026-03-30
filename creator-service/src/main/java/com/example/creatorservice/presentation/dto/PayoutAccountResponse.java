package com.example.creatorservice.presentation.dto;

import com.example.creatorservice.domain.model.Creator;
import java.util.UUID;

public record PayoutAccountResponse(
        UUID id,
        String bankName,
        String accountNumber,
        String accountHolder
) {
    public static PayoutAccountResponse from(Creator creator) {
        return new PayoutAccountResponse(
                creator.getId(),
                creator.getBankName(),
                creator.getAccountNumber(),
                creator.getAccountHolder()
        );
    }
}