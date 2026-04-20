package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.UserSync;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSyncRepository {
    void save(UserSync userSync);
    Optional<UserSync> findById(UUID userId);
    List<UserSync> findAllByIds(Collection<UUID> userIds);
    boolean existsById(UUID userId);
    void deleteById(UUID userId);
}