package com.example.rivewservice.infrastructure.messaging.consumer;

import com.example.rivewservice.common.exception.UserErrorCode;
import com.example.rivewservice.domain.model.User;
import com.example.rivewservice.domain.repository.UserRepository;
import com.example.rivewservice.infrastructure.messaging.dto.request.UserCreatedMessage;
import com.example.rivewservice.infrastructure.messaging.dto.request.UserDeletedMessage;
import com.example.rivewservice.infrastructure.messaging.dto.request.UserUpdatedMessage;
import com.example.rivewservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {
    private final UserRepository userRepository;
    private final KafkaMessageUtil kafkaMessageUtil;

    //유저 생성 동기화
    @KafkaListener(topics = "user.created", groupId = "review-service")
    public void createdConsumer(String message) {
        log.info("[user.created] 수신: {}", message);
        UserCreatedMessage userCreatedMessage = kafkaMessageUtil.deserialize(message,UserCreatedMessage.class);

        if (userRepository.existsById(userCreatedMessage.userId())) {
            log.warn("[user.created] 이미 존재하는 유저 - userId={}", userCreatedMessage.userId());
            throw UserErrorCode.USER_ALREADY_EXISTS.of(userCreatedMessage.userId());
        }

        User user = User.create(
                userCreatedMessage.userId(),
                userCreatedMessage.nickname(),
                userCreatedMessage.profileUrl()
        );
        userRepository.save(user);
        log.info("[user.created] 유저 저장 완료 - userId={}", userCreatedMessage.userId());
    }


    //유저 업데이트 동기화
    @KafkaListener(topics = "user.updated", groupId = "review-service")
    public void updatedConsumer(String message) {
        log.debug("[user.updated] 수신: {}", message);
        UserUpdatedMessage userUpdatedMessage = kafkaMessageUtil.deserialize(message, UserUpdatedMessage.class);

        User user = userRepository.findById(userUpdatedMessage.userId())
                .orElseThrow(() -> UserErrorCode.USER_NOT_FOUND.of(userUpdatedMessage.userId()));

        user.updateProfile(userUpdatedMessage.nickname(), userUpdatedMessage.profileUrl());
        userRepository.save(user);
        log.debug("[user.updated] 유저 업데이트 완료 - userId={}", userUpdatedMessage.userId());
    }


    //유저 삭제 동기화
    @KafkaListener(topics = "user.deleted", groupId = "review-service")
    public void deletedConsumer(String message) {
        log.debug("[user.deleted] 수신: {}", message);
        UserDeletedMessage userDeletedMessage = kafkaMessageUtil.deserialize(message, UserDeletedMessage.class);

        User user = userRepository.findById(userDeletedMessage.userId())
                .orElseThrow(() -> UserErrorCode.USER_NOT_FOUND.of(userDeletedMessage.userId()));

        user.delete();
        userRepository.save(user);
        log.debug("[user.deleted] 유저 삭제 완료 - userId={}", userDeletedMessage.userId());
    }

}
