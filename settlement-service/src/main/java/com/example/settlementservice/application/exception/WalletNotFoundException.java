package com.example.settlementservice.application.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID creatorId) {
        super("Wallet not found for creator: " + creatorId);
    }
}