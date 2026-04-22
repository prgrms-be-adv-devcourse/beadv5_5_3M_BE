package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.UserSync;
import com.example.movieservice.domain.repository.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserSyncRepositoryAdapter implements UserSyncRepository {

    private final UserSyncJpaRepository userSyncJpaRepository;

    @Override
    public void save(UserSync userSync) {
        userSyncJpaRepository.save(userSync);
    }

    @Override
    public Optional<UserSync> findById(UUID userId) {
        return userSyncJpaRepository.findById(userId);
    }

    @Override
    public List<UserSync> findAllByIds(Collection<UUID> userIds) {
        return userSyncJpaRepository.findAllById(userIds);
    }

    @Override
    public boolean existsById(UUID userId) {
        return userSyncJpaRepository.existsById(userId);
    }

    @Override
    public void deleteById(UUID userId) {
        userSyncJpaRepository.deleteById(userId);
    }
}