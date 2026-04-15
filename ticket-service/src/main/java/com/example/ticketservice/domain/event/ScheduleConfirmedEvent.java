package com.example.ticketservice.domain.event;

import com.example.ticketservice.domain.model.Schedule;

public record ScheduleConfirmedEvent(Schedule schedule) {
}
