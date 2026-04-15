package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.event.ScheduleConfirmedEvent;
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
        log.info("fjsakfjaslkfjaskfjsalkfjafjlaksjf");
        // DB 커밋 완료 후 Redis 저장을 위해 이벤트 발행
        eventPublisher.publishEvent(new ScheduleConfirmedEvent(schedule));

        log.debug("Schedule confirmed: scheduleId={}, tickets created: {}", request.scheduleId(), request.seats());
    }
}
