package com.example.streamingservice.infrastructure.messaging;

import com.example.streamingservice.application.port.SchedulerPort;
import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;
import com.example.streamingservice.infrastructure.messaging.dto.ScheduleConfirmedPayload;
import com.example.streamingservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleConfirmedListener {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ScheduleRepository scheduleRepository;
	private final SchedulerPort schedulerPort;
	private final KafkaMessageUtil kafkaMessageUtil;

	@RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000, multiplier = 2.0))
	@KafkaListener(topics = "movie.schedule.confirmed", groupId = "${spring.kafka.consumer.group-id}")
	@Transactional
	public void onMessage(@Payload String message) {
		ScheduleConfirmedPayload payload = kafkaMessageUtil.deserialize(message, ScheduleConfirmedPayload.class);
		Schedule schedule = scheduleRepository.findById(payload.scheduleId())
			.orElseGet(() -> scheduleRepository.save(new Schedule(
				payload.scheduleId(),
				payload.movieId(),
				payload.creatorId(),
				payload.title(),
				payload.startTime().atZone(KST).toInstant(),
				payload.endTime().atZone(KST).toInstant(),
				payload.seats(),
				payload.imageUrl())));
		schedulerPort.scheduleLifecycle(schedule);
	}

	@DltHandler
	public void dlt(String message, Exception e) {
		log.error("Schedule confirmed DLT: payload={}", message, e);
	}
}