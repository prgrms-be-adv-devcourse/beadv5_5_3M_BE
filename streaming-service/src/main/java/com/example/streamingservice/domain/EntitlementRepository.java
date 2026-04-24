package com.example.streamingservice.domain;

import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository {

	Optional<Entitlement> find(UUID userId, long scheduleId);

	Entitlement save(Entitlement entitlement);
}