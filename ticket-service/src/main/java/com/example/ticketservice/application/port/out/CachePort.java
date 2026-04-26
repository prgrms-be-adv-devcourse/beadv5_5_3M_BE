package com.example.ticketservice.application.port.out;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CachePort {

    void set(String key, Object value, Duration ttl);

    <T> Optional<T> get(String key, Class<T> type);

    boolean delete(String key);

    boolean exists(String key);

    void expireKey(String key, Duration ttl);

    // Counter 연산 (Redis String — 정수 인코딩)
    void setCounter(String key, long value, Duration ttl);

    Long increment(String key);

    Long decrement(String key);

    Long getCounter(String key);

    Map<String, Long> getCounters(Collection<String> keys);

    // Set 연산
    void addToSet(String key, String member);

    void removeFromSet(String key, String member);

    boolean isMemberOfSet(String key, String member);

    Set<String> getSetMembers(String key);

    Long getSetSize(String key);

    // ZSet (Sorted Set) 연산
    void addToZSetWithTimestamp(String key, String member);

    String popMinFromZSet(String key);

    Long getZSetRank(String key, String member);

    Long getZSetSize(String key);
}