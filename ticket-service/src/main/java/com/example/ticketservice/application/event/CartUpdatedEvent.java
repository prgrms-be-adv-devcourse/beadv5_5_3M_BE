package com.example.ticketservice.application.event;

import java.util.UUID;

// 장바구니 추가/제거 시 발행 (Kafka 알림 등 후속 처리용)
public record CartUpdatedEvent(
        Long scheduleId,
        UUID userId,
        boolean added
) {}