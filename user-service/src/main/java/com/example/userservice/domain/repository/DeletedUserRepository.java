package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.DeletedUser;

import java.util.UUID;

public interface DeletedUserRepository {

    void save(DeletedUser deletedUser);

    boolean existsByUserId(UUID userId);
}
