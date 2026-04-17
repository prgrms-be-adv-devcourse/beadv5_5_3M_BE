package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {
}
