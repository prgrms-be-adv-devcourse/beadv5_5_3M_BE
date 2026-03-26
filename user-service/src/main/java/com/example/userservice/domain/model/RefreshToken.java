package com.example.userservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@AllArgsConstructor
@RedisHash("refresh_token")
public class RefreshToken {

    @Id
    private String userId;

    private String token;

    @TimeToLive
    private Long expiry;  // seconds

    public static RefreshToken create(String userId, String token, Long expiry) {
        return new RefreshToken(userId, token, expiry);

    }
}
