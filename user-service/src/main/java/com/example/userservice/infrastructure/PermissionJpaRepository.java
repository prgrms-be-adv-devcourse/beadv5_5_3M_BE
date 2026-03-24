package com.example.userservice.infrastructure;

import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionJpaRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByRole(Role role);
}
