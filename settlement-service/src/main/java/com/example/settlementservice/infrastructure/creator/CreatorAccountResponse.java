package com.example.settlementservice.infrastructure.creator;

import java.util.UUID;

public record CreatorAccountResponse(
        UUID creatorId,
        String bankName,
        String accountNumber,
        String accountHolder
) {}