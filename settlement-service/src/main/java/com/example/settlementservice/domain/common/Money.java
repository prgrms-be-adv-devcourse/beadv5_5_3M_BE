package com.example.settlementservice.domain.common;

import java.math.BigDecimal;

public record Money(long value) {

    public Money {
        if (value < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + value);
        }
    }

    public static Money of(long value) {
        return new Money(value);
    }

    public static Money of(BigDecimal value) {
        return new Money(value.longValue());
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }

    public Money subtract(Money other) {
        if (this.value < other.value) {
            throw new IllegalStateException("Insufficient balance: " + this.value + " < " + other.value);
        }
        return new Money(this.value - other.value);
    }

    public boolean isLessThan(Money other) {
        return this.value < other.value;
    }
}