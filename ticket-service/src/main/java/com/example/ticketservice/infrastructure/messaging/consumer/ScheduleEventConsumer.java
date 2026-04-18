package com.example.ticketservice.infrastructure.messaging.consumer;

import com.example.ticketservice.application.event.ScheduleInitializedEvent;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
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
    @KafkaListener(topics = KafkaTopics.SCHEDULE_CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            ScheduleConfirmedMessage request = kafkaMessageUtil.deserialize(message, ScheduleConfirmedMessage.class);

            if (scheduleRepository.existsById(request.scheduleId())) {
                log.warn("중복 스케줄 수신 무시 - scheduleId={}", request.scheduleId());
                return;
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
        } catch (Exception e) {
            log.error("movie.schedule.confirmed 처리 실패 - message={}", message, e);
            // 정상 소비 처리하여 Kafka 재시도 무한 루프 방지
            // 복구 불가능한 오류는 로그 기반 모니터링으로 대응
        }
    }
}