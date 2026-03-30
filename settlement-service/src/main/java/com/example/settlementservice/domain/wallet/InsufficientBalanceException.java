package com.example.settlementservice.domain.wallet;

import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID creatorId, long balance, long requested) {
        super("Insufficient balance for creator " + creatorId +
              ": balance=" + balance + ", requested=" + requested);
    }
}