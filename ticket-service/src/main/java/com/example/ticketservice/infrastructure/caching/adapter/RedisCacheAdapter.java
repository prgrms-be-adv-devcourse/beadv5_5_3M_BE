package com.example.ticketservice.infrastructure.caching.adapter;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {

    private final StringRedisTemplate redisTemplate;
    private final KafkaMessageUtil messageUtil;

    @Override
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, messageUtil.serialize(value), ttl);
        log.debug("Redis 저장 - key: {}, ttl: {}", key, ttl);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.debug("Redis 캐시 미스 - key: {}", key);
            return Optional.empty();
        }
        log.debug("Redis 캐시 히트 - key: {}", key);
        return Optional.of(messageUtil.deserialize(value, type));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Redis 삭제 - key: {}", key);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void addToZSet(String key, Object member, double score) {
        redisTemplate.opsForZSet().add(key, messageUtil.serialize(member), score);
        log.debug("Redis ZSet 추가 - key: {}, score: {}", key, score);
    }

    @Override
    public void removeFromZSetByScore(String key, double score) {
        redisTemplate.opsForZSet().removeRangeByScore(key, score, score);
        log.debug("Redis ZSet 삭제 - key: {}, score: {}", key, score);
    }

    @Override
    public <T> Set<T> getZSetMembers(String key, Class<T> type) {
        Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
        if (members == null || members.isEmpty()) {
            log.debug("Redis ZSet 미스 - key: {}", key);
            return Set.of();
        }
        log.debug("Redis ZSet 히트 - key: {}, count: {}", key, members.size());
        return members.stream()
                .map(m -> messageUtil.deserialize(m, type))
                .collect(Collectors.toSet());
    }
}