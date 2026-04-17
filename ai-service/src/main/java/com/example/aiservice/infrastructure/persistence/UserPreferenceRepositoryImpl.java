package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.UserPreference;
import com.example.aiservice.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserPreferenceRepositoryImpl implements UserPreferenceRepository {

    private final UserPreferenceJpaRepository userPreferenceJpaRepository;

    @Override
    public boolean existsById(UUID userId) {
        return userPreferenceJpaRepository.existsById(userId);
    }

    @Override
    public UserPreference save(UserPreference userPreference) {
        return userPreferenceJpaRepository.save(userPreference);
    }

    @Override
    public void deleteById(UUID userId) {
        userPreferenceJpaRepository.deleteById(userId);
    }
}
