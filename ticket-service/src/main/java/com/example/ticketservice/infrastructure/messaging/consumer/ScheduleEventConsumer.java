package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.application.event.ScheduleInitializedEvent;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.messaging.dto.request.ScheduleConfirmedMessage;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventConsumer {

    private final ScheduleRepository scheduleRepository;
    private final KafkaMessageUtil kafkaMessageUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @KafkaListener(topics = "movie.schedule.confirmed", groupId = "ticket-group")
    public void consume(String message) {
        ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);

        if (scheduleRepository.existsById(request.scheduleId())) {
            throw ScheduleErrorCode.ALREADY_CONFIRMED.of(request.scheduleId());
        }

        Schedule schedule = Schedule.create(
                request.scheduleId(),
                request.startTime(),
                request.endTime(),
                request.ticketingTime(),
                request.title(),
                request.cookie(),
                request.creatorId(),
                request.movieId(),
                request.imageUrl(),
                request.seats()
        );
        scheduleRepository.save(schedule);

        eventPublisher.publishEvent(new ScheduleInitializedEvent(
                request.scheduleId(), request.ticketingTime(), request.startTime(), request.endTime()));

        log.debug("Schedule confirmed: scheduleId={}, seats={}", request.scheduleId(), request.seats());
    }
}