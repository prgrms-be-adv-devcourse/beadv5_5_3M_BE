package com.example.userservice.application.port;

import java.util.Optional;

public interface RedisPort {

    void saveRefreshToken(String userId, String token, long expirySeconds);

    Optional<String> findRefreshToken(String userId);

    void deleteRefreshToken(String userId);

    void saveAccessToken(String userId, String token, long expirySeconds);

    Optional<String> findAccessToken(String userId);

    void saveProfileImageUrl(String userId, String url);

    Optional<String> findProfileImageUrl(String userId);

    void saveEmailVerificationCode(String email, String code);

    Optional<String> findEmailVerificationCode(String email);

    void deleteEmailVerificationCode(String email);

    void saveEmailVerified(String email);

    boolean isEmailVerified(String email);

    void deleteEmailVerified(String email);
}
