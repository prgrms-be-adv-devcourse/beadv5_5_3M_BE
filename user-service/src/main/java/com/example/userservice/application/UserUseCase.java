package com.example.userservice.application;

import com.example.userservice.presentation.dto.req.AuthorizationRequest;

public interface UserUseCase {
    boolean checkAuthorization(AuthorizationRequest request, String userId);

}
