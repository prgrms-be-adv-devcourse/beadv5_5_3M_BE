package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.Creator;

import java.util.Optional;
import java.util.UUID;

public interface CreatorRepository {

    boolean existsById(UUID creatorId);

    Optional<Creator> findById(UUID creatorId);

    Creator save(Creator creator);
}
