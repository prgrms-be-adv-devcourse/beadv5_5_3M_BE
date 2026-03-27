package com.example.rivewservice.infrastructure.persistence;

import com.example.rivewservice.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {


    @Query("SELECT u.flag FROM User u WHERE u.userId = :userId")
    boolean getFlagByUserId(UUID userId);
}
