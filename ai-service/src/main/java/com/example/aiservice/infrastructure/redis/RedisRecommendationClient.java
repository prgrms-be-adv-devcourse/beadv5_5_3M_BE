package com.example.aiservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRecommendationClient {

    private static final String KEY_PREFIX = "recommendations:";

    private final StringRedisTemplate redisTemplate;

    public void deleteCache(UUID userId) {
        String key = KEY_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("[Redis] 추천 캐시 삭제 - userId: {}", userId);
        } else {
            log.debug("[Redis] 추천 캐시 없음 (이미 만료되었거나 미생성) - userId: {}", userId);
        }
    }
}
