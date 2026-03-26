package com.example.userservice.infrastructure.redis;

import com.example.userservice.domain.model.RefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRedisRepository extends CrudRepository<RefreshToken, String> {
}
