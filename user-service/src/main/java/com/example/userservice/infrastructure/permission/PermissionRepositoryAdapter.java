package com.example.userservice.infrastructure.permission;

import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.Role;
import com.example.userservice.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final PermissionJpaRepository permissionJpaRepository;

    @Override
    public List<Permission> findByRole(Role role) {
        return permissionJpaRepository.findByRole(role);
    }
}
