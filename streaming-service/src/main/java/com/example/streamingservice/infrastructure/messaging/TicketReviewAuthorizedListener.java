package com.example.streamingservice.infrastructure.messaging;

import com.example.streamingservice.domain.Entitlement;
import com.example.streamingservice.domain.EntitlementRepository;
import com.example.streamingservice.infrastructure.messaging.dto.TicketReviewAuthorizedPayload;
import com.example.streamingservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketReviewAuthorizedListener {

	private final EntitlementRepository entitlementRepository;
	private final KafkaMessageUtil kafkaMessageUtil;

	@RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000, multiplier = 2.0))
	@KafkaListener(topics = "ticket.review.authorized", groupId = "${spring.kafka.consumer.group-id}")
	@Transactional
	public void onMessage(@Payload String message) {
		TicketReviewAuthorizedPayload payload = kafkaMessageUtil.deserialize(message, TicketReviewAuthorizedPayload.class);
		if (entitlementRepository.find(payload.userId(), payload.scheduleId()).isPresent()) {
			return;
		}
		try {
			entitlementRepository.save(Entitlement.of(
				payload.userId(),
				payload.scheduleId(),
				payload.ticketId(),
				Instant.now()));
		} catch (DataIntegrityViolationException e) {
			log.debug("Entitlement already exists (race): user={}, schedule={}",
				payload.userId(), payload.scheduleId());
		}
	}

	@DltHandler
	public void dlt(String message, Exception e) {
		log.error("Ticket authorized DLT: payload={}", message, e);
	}
}