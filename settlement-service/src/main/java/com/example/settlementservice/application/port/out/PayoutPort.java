package com.example.settlementservice.application.port.out;

import com.example.settlementservice.domain.settlement.Settlement;

public interface PayoutPort {
    boolean payout(Settlement settlement);
}