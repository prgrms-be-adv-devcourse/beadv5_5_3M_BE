package com.example.userservice.infrastructure.redis;

import com.example.userservice.application.port.RedisPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisAdapter implements RedisPort {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String ACCESS_TOKEN_KEY_PREFIX = "access:token:";
    private static final String PROFILE_IMAGE_KEY_PREFIX = "profile:image:";
    private static final String EMAIL_CODE_KEY_PREFIX = "email:code:";
    private static final String EMAIL_VERIFIED_KEY_PREFIX = "email:verified:";
    private static final long EMAIL_CODE_TTL_MINUTES = 5;
    private static final long EMAIL_VERIFIED_TTL_MINUTES = 30;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void saveRefreshToken(String userId, String token, long expirySeconds) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + userId,
                token,
                expirySeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<String> findRefreshToken(String userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId)
        );
    }

    @Override
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
    }

    @Override
    public void saveAccessToken(String userId, String token, long expirySeconds) {
        redisTemplate.opsForValue().set(
                ACCESS_TOKEN_KEY_PREFIX + userId,
                token,
                expirySeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<String> findAccessToken(String userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(ACCESS_TOKEN_KEY_PREFIX + userId)
        );
    }

    @Override
    public void saveProfileImageUrl(String userId, String url) {
        redisTemplate.opsForValue().set(PROFILE_IMAGE_KEY_PREFIX + userId, url);
    }

    @Override
    public Optional<String> findProfileImageUrl(String userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(PROFILE_IMAGE_KEY_PREFIX + userId)
        );
    }

    @Override
    public void saveEmailVerificationCode(String email, String code) {
        redisTemplate.opsForValue().set(EMAIL_CODE_KEY_PREFIX + email, code, EMAIL_CODE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public Optional<String> findEmailVerificationCode(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(EMAIL_CODE_KEY_PREFIX + email));
    }

    @Override
    public void deleteEmailVerificationCode(String email) {
        redisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
    }

    @Override
    public void saveEmailVerified(String email) {
        redisTemplate.opsForValue().set(EMAIL_VERIFIED_KEY_PREFIX + email, "true", EMAIL_VERIFIED_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public boolean isEmailVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get(EMAIL_VERIFIED_KEY_PREFIX + email));
    }

    @Override
    public void deleteEmailVerified(String email) {
        redisTemplate.delete(EMAIL_VERIFIED_KEY_PREFIX + email);
    }
}
