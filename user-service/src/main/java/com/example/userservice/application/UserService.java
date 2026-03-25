package com.example.userservice.application;

import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.repository.PermissionRepository;
import com.example.userservice.domain.repository.UserRepository;
import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public boolean checkAuthorization(AuthorizationRequest request, String userId) {
        User findUser = userRepository.findById(toUUID(userId));
        List<Permission> permissions = permissionRepository.findByRole(findUser.getRole());

        return permissions.stream()
                .filter(p -> p.getHttpMethod() == null || p.getHttpMethod().equals(request.httpMethod().name()))
                .anyMatch(p -> request.requestPath().startsWith(p.getPathPattern()));
    }

    private UUID toUUID(String userId) {
        return UUID.fromString(userId);
    }
}
