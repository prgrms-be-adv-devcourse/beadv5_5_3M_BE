package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.Role;

import java.util.List;

public interface PermissionRepository {
    List<Permission> findByRole(Role role);
}
