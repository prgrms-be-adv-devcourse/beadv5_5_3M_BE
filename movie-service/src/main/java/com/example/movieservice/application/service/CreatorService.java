package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.CreatorUseCase;
import com.example.movieservice.domain.model.Creator;
import com.example.movieservice.domain.repository.CreatorRepository;
import com.example.movieservice.infrastructure.kafka.dto.consume.CreatorCreatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.CreatorUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorService implements CreatorUseCase {

    private final CreatorRepository creatorRepository;

    @Override
    @Transactional
    public void handleCreatorCreated(CreatorCreatedMessage msg) {
        if(creatorRepository.existsById(msg.creatorId())){
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
        Optional<Creator> creatorOpt = creatorRepository.findById(msg.creatorId());
        if(creatorOpt.isEmpty()){
            log.warn("[Kafka] 대상 크리에이터 없음 skip - creatorId: {}", msg.creatorId());
            return;
        }
        creatorOpt.get().update(msg.nickname());
    }
}
