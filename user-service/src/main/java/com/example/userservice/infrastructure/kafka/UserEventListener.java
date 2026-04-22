package com.example.userservice.infrastructure.kafka;

import com.example.userservice.application.port.KafkaPort;
import com.example.userservice.application.port.RedisPort;
import com.example.userservice.infrastructure.kafka.event.UserCreatedEvent;
import com.example.userservice.infrastructure.kafka.event.UserDeletedEvent;
import com.example.userservice.infrastructure.kafka.event.UserUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final KafkaPort kafkaPort;
    private final RedisPort redisPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserCreated(UserCreatedEvent event) {
        redisPort.deleteEmailVerified(event.email());
        kafkaPort.publish("user.created", event.userId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserUpdated(UserUpdatedEvent event) {
        if (event.profileUrl() != null) {
            redisPort.saveProfileImageUrl(event.userId().toString(), event.profileUrl());
        }
        kafkaPort.publish("user.updated", event.userId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDeleted(UserDeletedEvent event) {
        redisPort.deleteRefreshToken(event.userId().toString());
        kafkaPort.publish("user.deleted", event.userId().toString(), event);
    }
}
