package com.example.settlementservice.domain.settlement;

public class InvalidSettlementStateException extends RuntimeException {
    public InvalidSettlementStateException(Long settlementId, SettlementStatus current, SettlementStatus required) {
        super("Settlement " + settlementId + " is in state " + current + ", required: " + required);
    }
}