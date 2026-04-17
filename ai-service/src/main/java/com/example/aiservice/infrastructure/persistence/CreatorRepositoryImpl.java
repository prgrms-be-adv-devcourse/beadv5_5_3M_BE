package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.Creator;
import com.example.aiservice.domain.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CreatorRepositoryImpl implements CreatorRepository {

    private final CreatorJpaRepository creatorJpaRepository;

    @Override
    public boolean existsById(UUID creatorId) {
        return creatorJpaRepository.existsById(creatorId);
    }

    @Override
    public Optional<Creator> findById(UUID creatorId) {
        return creatorJpaRepository.findById(creatorId);
    }

    @Override
    public Creator save(Creator creator) {
        return creatorJpaRepository.save(creator);
    }
}
