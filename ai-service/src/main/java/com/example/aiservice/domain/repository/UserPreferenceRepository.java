package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.UserPreference;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository {

    boolean existsById(UUID userId);

    UserPreference save(UserPreference userPreference);

    void deleteById(UUID userId);
}
