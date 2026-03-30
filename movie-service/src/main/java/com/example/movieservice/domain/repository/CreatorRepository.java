package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Creator;

import java.util.Optional;
import java.util.UUID;

public interface CreatorRepository {
    boolean existsById(UUID creatorId);

    void save(Creator creator);

    Optional<Creator> findById(UUID creatorId);
}
