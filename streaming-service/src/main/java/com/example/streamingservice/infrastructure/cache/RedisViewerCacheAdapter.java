package com.example.streamingservice.infrastructure.cache;

import com.example.streamingservice.application.constants.RedisKeys;
import com.example.streamingservice.application.port.ViewerCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisViewerCacheAdapter implements ViewerCachePort {

	private final StringRedisTemplate redisTemplate;

	@Override
	public long add(long scheduleId, UUID userId) {
		Long added = redisTemplate.opsForSet().add(RedisKeys.viewers(scheduleId), userId.toString());
		return added == null ? 0L : added;
	}

	@Override
	public long remove(long scheduleId, UUID userId) {
		Long removed = redisTemplate.opsForSet().remove(RedisKeys.viewers(scheduleId), userId.toString());
		return removed == null ? 0L : removed;
	}

	@Override
	public long count(long scheduleId) {
		return Optional.ofNullable(redisTemplate.opsForSet().size(RedisKeys.viewers(scheduleId)))
			.orElse(0L);
	}

	@Override
	public Set<UUID> snapshot(long scheduleId) {
		Set<String> members = redisTemplate.opsForSet().members(RedisKeys.viewers(scheduleId));
		if (members == null || members.isEmpty()) {
			return Set.of();
		}
		return members.stream()
			.map(UUID::fromString)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public void purge(long scheduleId) {
		redisTemplate.delete(RedisKeys.viewers(scheduleId));
	}

	@Override
	public Set<Long> activeScheduleIds() {
		Set<Long> ids = new HashSet<>();
		ScanOptions opts = ScanOptions.scanOptions()
			.match(RedisKeys.VIEWERS_PREFIX + "*")
			.count(200)
			.build();
		try (Cursor<String> cursor = redisTemplate.scan(opts)) {
			while (cursor.hasNext()) {
				String key = cursor.next();
				String id = key.substring(RedisKeys.VIEWERS_PREFIX.length());
				try {
					ids.add(Long.parseLong(id));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return ids;
	}
}