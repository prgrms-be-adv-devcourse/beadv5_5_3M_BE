package com.example.streamingservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.example.streamingservice.domain.Entitlement;
import com.example.streamingservice.domain.EntitlementRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EntitlementRepositoryImpl implements EntitlementRepository {

	private final EntitlementJpaRepository jpaRepository;

	@Override
	public Optional<Entitlement> find(UUID userId, long scheduleId) {
		return jpaRepository.findByUserIdAndScheduleId(userId, scheduleId);
	}

	@Override
	public Entitlement save(Entitlement entitlement) {
		return jpaRepository.save(entitlement);
	}
}