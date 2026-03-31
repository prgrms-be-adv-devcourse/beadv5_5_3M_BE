package com.example.settlementservice.application.port.out;

import java.util.UUID;

public record CreatorPayoutSnapshot(
        UUID creatorId,
        String bankName,
        String accountNumber,
        String accountHolder
) {}