package com.example.userservice.infrastructure.redis;

import com.example.userservice.domain.repository.RefreshTokenRepository;
import com.example.userservice.domain.model.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Override
    public void save(RefreshToken refreshToken) {
        refreshTokenRedisRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByUserId(UUID userId) {
        return refreshTokenRedisRepository.findById(userId.toString());
    }
}
