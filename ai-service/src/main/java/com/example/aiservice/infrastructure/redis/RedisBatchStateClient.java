package com.example.aiservice.infrastructure.redis;

import com.example.aiservice.domain.model.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBatchStateClient {

    private static final String BATCH_ID_KEY_PREFIX = "batch:state:batch_id:";
    private static final String TARGET_USERS_KEY_PREFIX = "batch:state:target_users:";
    private static final String CANDIDATE_KEY_PREFIX = "batch:state:candidates:";
    private static final Duration TTL = Duration.ofHours(26);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveBatchId(String batchId) {
        String key = BATCH_ID_KEY_PREFIX + LocalDate.now();
        redisTemplate.opsForValue().set(key, batchId, TTL);
        log.debug("[Redis] 배치 ID 저장 - date: {}, batchId: {}", LocalDate.now(), batchId);
    }

    public String getBatchId() {
        String key = BATCH_ID_KEY_PREFIX + LocalDate.now().minusDays(1);
        return redisTemplate.opsForValue().get(key);
    }

    public void saveTargetUserIds(List<UUID> userIds) {
        try {
            String key = TARGET_USERS_KEY_PREFIX + LocalDate.now();
            String json = objectMapper.writeValueAsString(userIds);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("[Redis] 대상 유저 목록 저장 실패", e);
        }
    }

    public List<UUID> getTargetUserIds() {
        String key = TARGET_USERS_KEY_PREFIX + LocalDate.now().minusDays(1);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Redis] 대상 유저 목록 역직렬화 실패", e);
            return Collections.emptyList();
        }
    }

    /**
     * 유저별 후보 메타데이터를 저장한다 (movieId → CandidateInfo).
     * LLM re-ranking 결과 수령 시 is_exploration / explorationSource 복원에 사용.
     */
    public void saveCandidateMeta(UUID userId, Map<Long, CandidateInfo> meta) {
        try {
            String json = objectMapper.writeValueAsString(meta);
            redisTemplate.opsForValue().set(CANDIDATE_KEY_PREFIX + userId, json, TTL);
        } catch (Exception e) {
            log.warn("[Redis] 후보 메타 저장 실패 - userId: {}", userId, e);
        }
    }

    public Map<Long, CandidateInfo> getCandidateMeta(UUID userId) {
        String json = redisTemplate.opsForValue().get(CANDIDATE_KEY_PREFIX + userId);
        if (json == null) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Redis] 후보 메타 역직렬화 실패 - userId: {}", userId, e);
            return Map.of();
        }
    }

    /**
     * 배치 ID를 삭제한다.
     * 유저별 candidates 키는 TTL 26h 자연 만료에 맡긴다 (SCAN 없이 처리).
     */
    public void clear() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        redisTemplate.delete(BATCH_ID_KEY_PREFIX + yesterday);
        redisTemplate.delete(TARGET_USERS_KEY_PREFIX + yesterday);
        log.debug("[Redis] 배치 상태 초기화");
    }
}
