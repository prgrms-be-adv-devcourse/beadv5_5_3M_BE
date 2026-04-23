package com.example.streamingservice.infrastructure.cache;

import com.example.streamingservice.application.constants.RedisKeys;
import com.example.streamingservice.application.port.ChatRateLimitPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisChatRateLimitAdapter implements ChatRateLimitPort {

	private final StringRedisTemplate redisTemplate;
	private final int limitPerSec;

	public RedisChatRateLimitAdapter(
		StringRedisTemplate redisTemplate,
		@Value("${streaming.chat.rate-limit-per-second}") int limitPerSec
	) {
		this.redisTemplate = redisTemplate;
		this.limitPerSec = limitPerSec;
	}

	@Override
	public boolean tryAcquire(UUID userId) {
		String key = RedisKeys.chatRateLimit(userId);
		Long count = redisTemplate.opsForValue().increment(key);
		if (count == null) {
			return false;
		}
		if (count == 1L) {
			redisTemplate.expire(key, Duration.ofSeconds(1));
		}
		return count <= limitPerSec;
	}
}