package com.example.ticketservice.infrastructure.caching.adapter;

import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    public void expireKey(String key, Duration ttl) {
        redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("Redis TTL 설정 - key: {}, ttl: {}", key, ttl);
    }

    @Override
    public void setCounter(String key, long value, Duration ttl) {
        redisTemplate.opsForValue().set(key, String.valueOf(value), ttl);
        log.debug("Redis Counter 초기화 - key: {}, value: {}, ttl: {}", key, value, ttl);
    }

    @Override
    public Long increment(String key) {
        Long result = redisTemplate.opsForValue().increment(key);
        log.debug("Redis INCR - key: {}, result: {}", key, result);
        return result;
    }

    @Override
    public Long decrement(String key) {
        Long result = redisTemplate.opsForValue().decrement(key);
        log.debug("Redis DECR - key: {}, result: {}", key, result);
        return result;
    }

    @Override
    public Long getCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return Long.parseLong(value);
    }

    @Override
    public void addToSet(String key, String member) {
        redisTemplate.opsForSet().add(key, member);
        log.debug("Redis Set 추가 - key: {}, member: {}", key, member);
    }

    @Override
    public void removeFromSet(String key, String member) {
        redisTemplate.opsForSet().remove(key, member);
        log.debug("Redis Set 삭제 - key: {}, member: {}", key, member);
    }

    @Override
    public boolean isMemberOfSet(String key, String member) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, member));
    }

    @Override
    public Set<String> getSetMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Set.of();
    }

    @Override
    public Long getSetSize(String key) {
        return redisTemplate.opsForSet().size(key);
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

    @Override
    public void addToZSetWithTimestamp(String key, String member) {
        redisTemplate.opsForZSet().add(key, member, System.currentTimeMillis());
        log.debug("Redis ZSet 타임스탬프 추가 - key: {}, member: {}", key, member);
    }

    @Override
    public String popMinFromZSet(String key) {
        ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet().popMin(key);
        return tuple != null ? tuple.getValue() : null;
    }

    @Override
    public Long getZSetRank(String key, String member) {
        return redisTemplate.opsForZSet().rank(key, member);
    }

    @Override
    public Long getZSetSize(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }
}