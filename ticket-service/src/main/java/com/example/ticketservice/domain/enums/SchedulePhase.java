package com.example.ticketservice.domain.enums;

public enum SchedulePhase {
    CART_PERIOD,
    PROVISIONAL_PAYMENT,
    TICKETING_PERIOD;

    public static SchedulePhase from(ScheduleStatus status) {
        return switch (status) {
            case CART -> CART_PERIOD;
            case IN_PROGRESSING -> PROVISIONAL_PAYMENT;
            case TICKETING -> TICKETING_PERIOD;
            default -> throw new IllegalArgumentException(
                    "SchedulePhase 매핑 불가 status: " + status);
        };
    }
}