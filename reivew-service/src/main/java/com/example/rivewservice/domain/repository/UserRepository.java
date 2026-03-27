package com.example.rivewservice.domain.repository;

import com.example.rivewservice.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID userId);

    boolean existsById(UUID userId);
}