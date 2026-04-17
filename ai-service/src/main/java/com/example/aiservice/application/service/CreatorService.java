package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.CreatorUseCase;
import com.example.aiservice.domain.model.Creator;
import com.example.aiservice.domain.repository.CreatorRepository;
import com.example.aiservice.global.exception.ErrorStatus;
import com.example.aiservice.global.exception.GeneralException;
import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorService implements CreatorUseCase {

    private final CreatorRepository creatorRepository;

    @Override
    @Transactional
    public void handleCreatorCreated(CreatorCreatedMessage msg) {
        if (creatorRepository.existsById(msg.creatorId())) {
            log.warn("[Kafka] 중복 메시지 skip - creatorId: {}", msg.creatorId());
            return;
        }
        Creator creator = Creator.builder()
                .creatorId(msg.creatorId())
                .nickname(msg.nickname())
                .build();

        creatorRepository.save(creator);
    }

    @Override
    @Transactional
    public void handleCreatorUpdated(CreatorUpdatedMessage msg) {
        creatorRepository.findById(msg.creatorId())
                .ifPresentOrElse(
                        creator -> creator.update(msg.nickname()),
                        () -> log.warn("[Kafka] 대상 크리에이터 없음 skip - creatorId: {}", msg.creatorId())
                );
    }
}
