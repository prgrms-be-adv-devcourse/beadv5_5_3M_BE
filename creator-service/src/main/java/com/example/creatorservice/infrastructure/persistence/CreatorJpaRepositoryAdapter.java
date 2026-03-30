package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.application.exception.CreatorNotFoundException;
import com.example.creatorservice.domain.repository.CreatorRepository;
import com.example.creatorservice.domain.model.Creator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CreatorJpaRepositoryAdapter implements CreatorRepository {

    private final CreatorJpaRepository creatorJpaRepository;

    //join
    @Override
    public void save(Creator creator) {
        creatorJpaRepository.save(creator);
    }

    @Override
    public boolean existsByEmail(String email) {
        return creatorJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return creatorJpaRepository.existsByNickname(nickname);
    }

    //login
    @Override
    public Creator findByEmail(String email) {
        return creatorJpaRepository.findByEmail(email)
                .orElseThrow(CreatorNotFoundException::new);
    }

    @Override
    public Creator findById(UUID creatorId) {
        return creatorJpaRepository.findById(creatorId)
                .orElseThrow(CreatorNotFoundException::new);
    }

}