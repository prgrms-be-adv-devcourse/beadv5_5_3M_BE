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
}
