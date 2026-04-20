package com.example.ticketservice.infrastructure.messaging.dto.event;

// topic: queue.terminated
// receiver: notification-service (대기열 대기자 전원 실패 알림)
public record QueueTerminatedMessage(Long scheduleId) {}