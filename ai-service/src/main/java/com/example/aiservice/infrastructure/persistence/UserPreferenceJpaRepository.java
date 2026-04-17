package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPreferenceJpaRepository extends JpaRepository<UserPreference, UUID> {
}
