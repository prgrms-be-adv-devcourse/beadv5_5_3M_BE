package com.example.userservice.application;

import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import com.example.userservice.presentation.dto.req.LoginRequest;
import com.example.userservice.presentation.dto.res.LoginResponse;
import com.example.userservice.presentation.dto.res.TokenResponse;

import java.util.UUID;

public interface UserUseCase {
    boolean checkAuthorization(AuthorizationRequest request, String userId);
    void checkEmailDuplicate(String email);
    void checkNicknameDuplicate(String nickname);
    UUID join(JoinRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
}
