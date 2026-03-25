package com.example.ticketservice.common.exception;

public class ScheduleAlreadyConfirmed extends RuntimeException {
    public ScheduleAlreadyConfirmed(Long scheduleId) {
        super("Schedule already confirmed: " + scheduleId);
    }
}
