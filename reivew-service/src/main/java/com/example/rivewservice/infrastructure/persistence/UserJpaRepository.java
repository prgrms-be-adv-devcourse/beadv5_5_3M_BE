package com.example.rivewservice.infrastructure.persistence;

import com.example.rivewservice.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID>
{
}
