package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.common.exception.ScheduleAlreadyConfirmed;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.messaging.dto.request.ScheduleConfirmedMessage;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleEventConsumer {
    private final ScheduleRepository scheduleRepository;
    private final KafkaMessageUtil kafkaMessageUtil;

    @KafkaListener(topics = "movie.schedule.confirmed", groupId = "ticket-group")
    public void consume(String message) {
        ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);

        if (scheduleRepository.existsById(request.scheduleId())) {
            throw new ScheduleAlreadyConfirmed(request.scheduleId());
        }

        Schedule schedule = Schedule.create(
                request.scheduleId(),
                request.startTime(),
                request.endTime(),
                request.title(),
                request.cookie(),
                request.creatorId(),
                request.movieId(),
                request.imageUrl(),
                request.seats()
        );
        scheduleRepository.save(schedule);

        log.debug("Schedule confirmed: {}", request.scheduleId());
    }
}
