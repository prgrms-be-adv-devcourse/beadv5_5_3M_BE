package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.domain.model.ReviewAuthorization;
import com.example.movieservice.domain.repository.ReviewAuthorizationRepository;
import com.example.movieservice.domain.repository.UserSyncRepository;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewAuthorizedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAuthConsumer {

    private final ObjectMapper objectMapper;
    private final ReviewAuthorizationRepository reviewAuthorizationRepository;
    private final UserSyncRepository userSyncRepository;

    @KafkaListener(topics = "ticket.review.authorized", groupId = "movie-service")
    @Transactional
    public void consumeReviewAuthorized(String message) {
        try {
            ReviewAuthorizedMessage msg = objectMapper.readValue(message, ReviewAuthorizedMessage.class);

            if (!userSyncRepository.existsById(msg.userId())) {
                log.warn("[Kafka] ticket.review.authorized - 존재하지 않는 유저, skip - userId: {}", msg.userId());
                return;
            }

            if (reviewAuthorizationRepository.existsByTicketId(msg.ticketId())) {
                log.info("[Kafka] ticket.review.authorized - 이미 권한 존재, skip - ticketId: {}", msg.ticketId());
                return;
            }

            ReviewAuthorization authorization = ReviewAuthorization.builder()
                    .ticketId(msg.ticketId())
                    .userId(msg.userId())
                    .movieId(msg.movieId())
                    .scheduleId(msg.scheduleId())
                    .build();
            reviewAuthorizationRepository.save(authorization);
            log.info("[Kafka] ticket.review.authorized 처리 완료 - ticketId: {}, userId: {}, movieId: {}", msg.ticketId(), msg.userId(), msg.movieId());
        } catch (Exception e) {
            log.error("[Kafka] ticket.review.authorized 처리 실패 - payload: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}