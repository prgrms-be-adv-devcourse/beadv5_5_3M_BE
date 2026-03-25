package com.example.userservice.application;

import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;

import java.util.UUID;

public interface UserUseCase {
    boolean checkAuthorization(AuthorizationRequest request, String userId);
    void checkEmailDuplicate(String email);
    void checkNicknameDuplicate(String nickname);
    UUID join(JoinRequest request);
}
