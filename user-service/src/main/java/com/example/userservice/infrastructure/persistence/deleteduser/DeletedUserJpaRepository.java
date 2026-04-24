package com.example.userservice.infrastructure.persistence.deleteduser;

import com.example.userservice.domain.model.DeletedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeletedUserJpaRepository extends JpaRepository<DeletedUser, UUID> {
}
