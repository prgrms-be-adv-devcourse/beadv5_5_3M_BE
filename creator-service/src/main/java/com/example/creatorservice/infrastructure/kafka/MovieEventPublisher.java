package com.example.creatorservice.infrastructure.kafka;

import com.example.creatorservice.infrastructure.kafka.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishMovieUploaded(MovieUploadedMessage message) {
        publish("movie.uploaded", message.movieId().toString(), message);
    }

    public void publishMovieUpdated(MovieUpdatedMessage message) {
        publish("movie.updated", message.movieId().toString(), message);
    }

    public void publishMovieDeleted(MovieDeletedMessage message) {
        publish("movie.deleted", message.movieId().toString(), message);
    }

    public void publishMovieVisibilityChanged(MovieVisibilityChangedMessage message) {
        publish("movie.visibility", message.movieId().toString(), message);
    }

    public void publishScheduleConfirmed(ScheduleConfirmedMessage message) {
        publish("movie.schedule.confirmed", message.scheduleId().toString(), message);
    }

    private void publish(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Kafka] 발행 실패 - topic: {}, key: {}", topic, key, ex);
                        } else {
                            log.info("[Kafka] 발행 성공 - topic: {}, key: {}, offset: {}",
                                    topic, key, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("[Kafka] 직렬화 실패 - topic: {}, key: {}", topic, key, e);
            throw new IllegalStateException("Kafka 메시지 발행에 실패했습니다.", e);
        }
    }
}