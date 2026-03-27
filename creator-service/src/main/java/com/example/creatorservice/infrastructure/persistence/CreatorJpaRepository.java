package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {
    Optional<Creator> findByEmail(String email);
    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}