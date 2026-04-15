package com.example.userservice.application.usecase;

import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.DeductCookieRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import com.example.userservice.presentation.dto.req.LoginRequest;
import com.example.userservice.presentation.dto.req.RefundCookieRequest;
import com.example.userservice.presentation.dto.res.DeductCookieResponse;
import com.example.userservice.presentation.dto.res.RefundCookieResponse;
import com.example.userservice.presentation.dto.res.TokenResponse;
import com.example.userservice.presentation.dto.res.UserInfoResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserUseCase {
    boolean checkAuthorization(AuthorizationRequest request, String userId);
    void checkEmailDuplicate(String email);
    void checkNicknameDuplicate(String nickname);
    UUID join(JoinRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    UserInfoResponse me(String userId);
    void updateProfile(String userId, String nickname, String phone, MultipartFile profileImage);

    DeductCookieResponse deductCookie(DeductCookieRequest request);
    RefundCookieResponse refundCookie(RefundCookieRequest request);
    void logout(String userId);
}
