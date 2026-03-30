package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Creator;
import com.example.movieservice.domain.repository.CreatorRepository;
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
    public void save(Creator creator) {
        creatorJpaRepository.save(creator);
    }

    @Override
    public Optional<Creator> findById(UUID creatorId) {
        return creatorJpaRepository.findById(creatorId);
    }
}
