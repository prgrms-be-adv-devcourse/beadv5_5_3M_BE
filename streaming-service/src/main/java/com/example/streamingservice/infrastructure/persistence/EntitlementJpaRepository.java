package com.example.streamingservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.streamingservice.domain.Entitlement;

public interface EntitlementJpaRepository extends JpaRepository<Entitlement, Long> {

	Optional<Entitlement> findByUserIdAndScheduleId(UUID userId, long scheduleId);
}