package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.UserSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSyncJpaRepository extends JpaRepository<UserSync, UUID> {
}