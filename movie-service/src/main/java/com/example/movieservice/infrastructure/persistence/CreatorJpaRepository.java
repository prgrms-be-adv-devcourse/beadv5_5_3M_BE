package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {
}
