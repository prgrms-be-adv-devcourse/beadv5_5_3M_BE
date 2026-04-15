package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.response.QueueEntryResponse;
import com.example.ticketservice.application.dto.response.QueuePositionResponse;

import java.util.UUID;

public interface QueueUseCase {

    // stock > 0: 바로 구매, stock==0 AND RESERVED>0: 대기열 진입, stock==0 AND RESERVED==0: SOLD_OUT
    QueueEntryResponse enter(UUID userId, Long scheduleId);

    QueuePositionResponse getPosition(UUID userId, Long scheduleId);
}