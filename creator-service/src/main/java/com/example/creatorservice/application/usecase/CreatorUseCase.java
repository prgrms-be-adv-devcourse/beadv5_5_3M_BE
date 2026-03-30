package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.req.AuthorizationRequest;
import com.example.creatorservice.presentation.dto.req.JoinRequest;
import com.example.creatorservice.presentation.dto.req.LoginRequest;
import com.example.creatorservice.presentation.dto.res.TokenResponse;

import java.util.UUID;

public interface CreatorUseCase {

    UUID join(JoinRequest request);

    void checkEmailDuplicate(String email);

    void checkNicknameDuplicate(String nickname);

    TokenResponse login(LoginRequest request);

    boolean checkAuthorization(AuthorizationRequest request, String creatorId);

    TokenResponse refresh(String refreshToken);
}
