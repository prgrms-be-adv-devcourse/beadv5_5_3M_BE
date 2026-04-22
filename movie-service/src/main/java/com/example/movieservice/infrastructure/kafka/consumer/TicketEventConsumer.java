package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.infrastructure.kafka.dto.consume.TicketCancelledMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.TicketReservedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventConsumer {

    private final ScheduleUseCase scheduleUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ticket.reserved", groupId = "movie-service")
    @Transactional
    public void consumeTicketReserved(String message) {
        TicketReservedMessage msg;
        try {
            msg = objectMapper.readValue(message, TicketReservedMessage.class);
        } catch (JacksonException e) {
            // poison pill — 파싱 불가 메시지는 skip하여 무한 retry 방지
            // TODO: ticket.reserved.DLQ 토픽으로 이동 권장
            log.error("[Kafka] ticket.reserved 파싱 실패(poison pill), skip - payload: {}", message, e);
            return;
        }

        log.info("[Kafka] ticket.reserved 수신 - scheduleId: {}", msg.scheduleId());
        // 비즈니스 실패는 throw → Kafka가 재시도
        // TODO: eventId 기반 idempotency 적용 고려 (중복 소비 방지)
        scheduleUseCase.decreaseSeat(msg.scheduleId());
        log.info("[Kafka] ticket.reserved 처리 완료 - scheduleId: {}", msg.scheduleId());
    }

    @KafkaListener(topics = "ticket.cancelled", groupId = "movie-service")
    @Transactional
    public void consumeTicketCancelled(String message) {
        TicketCancelledMessage msg;
        try {
            msg = objectMapper.readValue(message, TicketCancelledMessage.class);
        } catch (JacksonException e) {
            // poison pill — 파싱 불가 메시지는 skip하여 무한 retry 방지
            // TODO: ticket.cancelled.DLQ 토픽으로 이동 권장
            log.error("[Kafka] ticket.cancelled 파싱 실패(poison pill), skip - payload: {}", message, e);
            return;
        }

        log.info("[Kafka] ticket.cancelled 수신 - scheduleId: {}", msg.scheduleId());
        // TODO: eventId 기반 idempotency 적용 고려 (중복 소비 방지)
        scheduleUseCase.increaseSeat(msg.scheduleId());
        log.info("[Kafka] ticket.cancelled 처리 완료 - scheduleId: {}", msg.scheduleId());
    }
}
