package com.example.settlementservice.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record FeePolicy(BigDecimal rate) {

    public Money calculateFee(Money gross) {
        BigDecimal fee = BigDecimal.valueOf(gross.value())
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP);
        return new Money(fee.longValue());
    }
}