package com.example.ticketservice.domain.enums;

public enum SchedulePhase {
    CART_PERIOD,
    PROVISIONAL_PAYMENT,
    TICKETING_PERIOD,
    LOBBY_WAITING,
    STREAMING_PERIOD,
    FINISHED;

    public static SchedulePhase from(ScheduleStatus status) {
        return switch (status) {
            case CART -> CART_PERIOD;
            case IN_PROGRESSING -> PROVISIONAL_PAYMENT;
            case TICKETING -> TICKETING_PERIOD;
            case LOBBY -> LOBBY_WAITING;
            case STREAMING -> STREAMING_PERIOD;
            case FINISH -> FINISHED;
        };
    }
}