package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Creator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorRepository {
    boolean existsById(UUID creatorId);

    void save(Creator creator);

    Optional<Creator> findById(UUID creatorId);

    List<Creator> findAllByCreatorIdIn(List<UUID> creatorIds);
}
