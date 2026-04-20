package com.example.aiservice.infrastructure.redis;

import com.example.aiservice.presentation.dto.response.RecommendationItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRecommendationClient {

    private static final String KEY_PREFIX = "recommendations:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<RecommendationItemResponse> get(UUID userId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Redis] 캐시 역직렬화 실패 - userId: {}, 캐시 삭제", userId);
            deleteCache(userId);
            return null;
        }
    }

    public void set(UUID userId, List<RecommendationItemResponse> items) {
        try {
            String value = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, value, ttlUntilMidnight());
            log.debug("[Redis] 추천 캐시 저장 - userId: {}, 개수: {}", userId, items.size());
        } catch (Exception e) {
            log.warn("[Redis] 캐시 저장 실패 - userId: {}", userId, e);
        }
    }

    public void deleteCache(UUID userId) {
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + userId);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("[Redis] 추천 캐시 삭제 - userId: {}", userId);
        } else {
            log.debug("[Redis] 추천 캐시 없음 (이미 만료되었거나 미생성) - userId: {}", userId);
        }
    }

    // 다음 자정까지 남은 시간 (7번 배치 구현 시 OpenAI Batch API deadline에 맞춰 변경 가능)
    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
        return Duration.between(now, nextMidnight);
    }
}
