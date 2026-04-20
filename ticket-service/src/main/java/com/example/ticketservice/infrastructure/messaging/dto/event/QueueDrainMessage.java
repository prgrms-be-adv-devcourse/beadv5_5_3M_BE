package com.example.ticketservice.infrastructure.messaging.dto.event;

// topic: queue.drain (internal)
// key: scheduleId → 동일 스케줄의 드레인 신호가 동일 파티션으로 라우팅되어 순서 보장
public record QueueDrainMessage(Long scheduleId) {}
