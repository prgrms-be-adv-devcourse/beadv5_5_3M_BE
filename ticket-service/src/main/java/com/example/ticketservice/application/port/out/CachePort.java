package com.example.ticketservice.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public interface CachePort {

    void set(String key, Object value, Duration ttl);

    <T> Optional<T> get(String key, Class<T> type);

    void delete(String key);

    boolean exists(String key);

    // ZSet (Sorted Set) 연산
    void addToZSet(String key, Object member, double score);

    void removeFromZSetByScore(String key, double score);

    <T> Set<T> getZSetMembers(String key, Class<T> type);
}