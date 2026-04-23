package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.ScheduleConfirmedMessage;

public record ScheduleConfirmedEvent(ScheduleConfirmedMessage message) {}