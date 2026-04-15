package com.example.userservice.infrastructure.persistence.user;

import com.example.userservice.domain.model.User;
import com.example.userservice.domain.repository.UserRepository;
import com.example.userservice.application.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public User findById(UUID userId) {
        return userJpaRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    public User findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
    }

    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByNickname(nickname);
    }

    public void save(User user) {
        userJpaRepository.save(user);
    }
}
