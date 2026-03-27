package com.example.settlementservice.application.exception;

import java.util.UUID;

public class CreatorPayoutAccountNotFoundException extends RuntimeException {
    public CreatorPayoutAccountNotFoundException(UUID creatorId) {
        super("Creator payout account not found for creator: " + creatorId);
    }
}