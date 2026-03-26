package com.example.settlementservice.application.exception;

public class SettlementNotFoundException extends RuntimeException {
    public SettlementNotFoundException(Long settlementId) {
        super("Settlement not found: " + settlementId);
    }
}