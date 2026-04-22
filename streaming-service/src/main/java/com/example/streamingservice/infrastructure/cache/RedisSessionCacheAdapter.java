package com.example.streamingservice.infrastructure.cache;

import com.example.streamingservice.application.constants.RedisKeys;
import com.example.streamingservice.application.dto.ActiveSession;
import com.example.streamingservice.application.dto.SessionMeta;
import com.example.streamingservice.application.port.SessionCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisSessionCacheAdapter implements SessionCachePort {

	private static final String FIELD_SESSION_ID = "sessionId";
	private static final String FIELD_SCHEDULE_ID = "scheduleId";
	private static final String FIELD_USER_ID = "userId";
	private static final String FIELD_ISSUED_AT = "issuedAt";

	private final StringRedisTemplate redisTemplate;

	@Override
	public void put(UUID userId, ActiveSession session, Duration ttl) {
		HashOperations<String, String, String> hash = redisTemplate.opsForHash();

		String userKey = RedisKeys.sessionByUser(userId);
		hash.putAll(userKey, Map.of(
			FIELD_SESSION_ID, session.sessionId().toString(),
			FIELD_SCHEDULE_ID, Long.toString(session.scheduleId()),
			FIELD_ISSUED_AT, Long.toString(session.issuedAt().toEpochMilli())
		));
		redisTemplate.expire(userKey, ttl);

		String sessionKey = RedisKeys.sessionById(session.sessionId());
		hash.putAll(sessionKey, Map.of(
			FIELD_USER_ID, userId.toString(),
			FIELD_SCHEDULE_ID, Long.toString(session.scheduleId())
		));
		redisTemplate.expire(sessionKey, ttl);
	}

	@Override
	public Optional<ActiveSession> findByUser(UUID userId) {
		Map<String, String> entries = redisTemplate.<String, String>opsForHash()
			.entries(RedisKeys.sessionByUser(userId));
		if (entries == null || entries.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ActiveSession(
			UUID.fromString(entries.get(FIELD_SESSION_ID)),
			Long.parseLong(entries.get(FIELD_SCHEDULE_ID)),
			Instant.ofEpochMilli(Long.parseLong(entries.get(FIELD_ISSUED_AT)))
		));
	}

	@Override
	public Optional<SessionMeta> findBySession(UUID sessionId) {
		Map<String, String> entries = redisTemplate.<String, String>opsForHash()
			.entries(RedisKeys.sessionById(sessionId));
		if (entries == null || entries.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SessionMeta(
			UUID.fromString(entries.get(FIELD_USER_ID)),
			Long.parseLong(entries.get(FIELD_SCHEDULE_ID))
		));
	}

	@Override
	public void evict(UUID userId, UUID sessionId) {
		HashOperations<String, String, String> hash = redisTemplate.opsForHash();
		UUID resolvedSessionId = sessionId;
		if (resolvedSessionId == null) {
			String stored = hash.get(RedisKeys.sessionByUser(userId), FIELD_SESSION_ID);
			if (stored != null) {
				resolvedSessionId = UUID.fromString(stored);
			}
		}
		redisTemplate.delete(RedisKeys.sessionByUser(userId));
		if (resolvedSessionId != null) {
			redisTemplate.delete(RedisKeys.sessionById(resolvedSessionId));
		}
	}

	@Override
	public Set<UUID> scanUsersByScheduleId(long scheduleId) {
		Set<UUID> users = new HashSet<>();
		String target = Long.toString(scheduleId);
		ScanOptions opts = ScanOptions.scanOptions()
			.match(RedisKeys.SESSION_BY_USER_PREFIX + "*")
			.count(200)
			.build();
		HashOperations<String, String, String> hash = redisTemplate.opsForHash();
		try (Cursor<String> cursor = redisTemplate.scan(opts)) {
			while (cursor.hasNext()) {
				String key = cursor.next();
				String value = hash.get(key, FIELD_SCHEDULE_ID);
				if (target.equals(value)) {
					String userId = key.substring(RedisKeys.SESSION_BY_USER_PREFIX.length());
					users.add(UUID.fromString(userId));
				}
			}
		}
		return users;
	}
}