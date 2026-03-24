package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.User;

import java.util.UUID;

public interface UserRepository {
    User findById(UUID userId);
}
