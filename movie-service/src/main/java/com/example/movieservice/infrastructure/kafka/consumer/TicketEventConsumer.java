package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.infrastructure.kafka.dto.consume.TicketCancelledMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.TicketReservedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventConsumer {

    private final ScheduleUseCase scheduleUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ticket.reserved", groupId = "movie-service")
    public void consumeTicketReserved(String message) {
        log.info("[Kafka] ticket.reserved 수신 - payload: {}", message);
        TicketReservedMessage msg = objectMapper.readValue(message, TicketReservedMessage.class);
        scheduleUseCase.decreaseSeat(msg.scheduleId());
        log.info("[Kafka] ticket.reserved 처리 완료 - scheduleId: {}", msg.scheduleId());
    }

    @KafkaListener(topics = "ticket.cancelled", groupId = "movie-service")
    public void consumeTicketCancelled(String message) {
        log.info("[Kafka] ticket.cancelled 수신 - payload: {}", message);
        TicketCancelledMessage msg = objectMapper.readValue(message, TicketCancelledMessage.class);
        scheduleUseCase.increaseSeat(msg.scheduleId());
        log.info("[Kafka] ticket.cancelled 처리 완료 - scheduleId: {}", msg.scheduleId());
    }
}
