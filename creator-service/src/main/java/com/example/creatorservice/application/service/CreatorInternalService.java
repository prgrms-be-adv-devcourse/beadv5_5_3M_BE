package com.example.creatorservice.application.service;

import com.example.creatorservice.application.usecase.CreatorInternalUseCase;
import com.example.creatorservice.domain.model.Creator;
import com.example.creatorservice.domain.repository.CreatorRepository;
import com.example.creatorservice.presentation.dto.PayoutAccountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorInternalService implements CreatorInternalUseCase {

    private final CreatorRepository creatorRepository;

    @Override
    public PayoutAccountResponse getPayoutAccount(UUID creatorId) {
        Creator creator = creatorRepository.findById(creatorId);
        return PayoutAccountResponse.from(creator);
    }
}
