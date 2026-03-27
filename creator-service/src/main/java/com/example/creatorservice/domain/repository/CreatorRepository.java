package com.example.creatorservice.domain.repository;

import com.example.creatorservice.domain.model.Creator;

import java.util.UUID;

public interface CreatorRepository {

    void save(Creator creator);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    Creator findByEmail(String email);

    Creator findById(UUID creatorId);
}