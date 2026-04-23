package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.UserPreference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository {

    boolean existsById(UUID userId);

    Optional<UserPreference> findById(UUID userId);

    UserPreference save(UserPreference userPreference);

    void deleteById(UUID userId);

    void incrementWatchCount(UUID userId);

    List<UserPreference> findAll();

    List<UserPreference> findAllByIds(List<UUID> userIds);

    void updateEpsilonAndCtr(UUID userId, double epsilon, double explorationClickRate);
}
