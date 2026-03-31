package com.example.rivewservice.infrastructure.persistence.impl;

import com.example.rivewservice.domain.model.User;
import com.example.rivewservice.domain.repository.UserRepository;
import com.example.rivewservice.infrastructure.persistence.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public boolean existsById(UUID userId) {
        return userJpaRepository.existsById(userId);
    }

    @Override
    public boolean getFlagByUserId(UUID userId) {
        return userJpaRepository.getFlagByUserId(userId);
    }
}
