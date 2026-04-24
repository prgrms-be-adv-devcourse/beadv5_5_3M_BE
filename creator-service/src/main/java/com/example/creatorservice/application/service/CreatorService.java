package com.example.creatorservice.application.service;

import com.example.creatorservice.application.exception.DuplicateEmailException;
import com.example.creatorservice.application.exception.DuplicateNicknameException;
import com.example.creatorservice.application.exception.InvalidEmailOrPasswordException;
import com.example.creatorservice.application.exception.InvalidRefreshTokenException;
import com.example.creatorservice.application.usecase.CreatorUseCase;
import com.example.creatorservice.domain.repository.CreatorRepository;
import com.example.creatorservice.domain.model.Creator;
import com.example.creatorservice.presentation.dto.req.AuthorizationRequest;
import com.example.creatorservice.event.CreatorCreatedEvent;
import com.example.creatorservice.presentation.dto.req.JoinRequest;
import com.example.creatorservice.presentation.dto.req.LoginRequest;
import com.example.creatorservice.presentation.dto.res.TokenResponse;
import com.example.creatorservice.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class CreatorService implements CreatorUseCase {

    private final CreatorRepository creatorRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProvider jwtProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UUID join(JoinRequest request) {
        if (creatorRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        if (creatorRepository.existsByNickname(request.nickname())) {
            throw new DuplicateNicknameException();
        }

        Creator creator = Creator.create(request);
        creatorRepository.save(creator);

        eventPublisher.publishEvent(CreatorCreatedEvent.from(creator));
        return creator.getId();
    }

    @Override
    public void checkEmailDuplicate(String email) {
        if (creatorRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
    }

    @Override
    public void checkNicknameDuplicate(String nickname) {
        if (creatorRepository.existsByNickname(nickname)) {
            throw new DuplicateNicknameException();
        }
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        Creator creator = creatorRepository.findByEmail(request.email());

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(request.password() + creator.getSaltKey(), creator.getPassword())) {
            throw new InvalidEmailOrPasswordException();
        }

        String accessToken = jwtProvider.generateAccessToken(creator.getId());
        String rawRefreshToken = jwtProvider.generateRefreshToken(creator.getId());
        redisTemplate.opsForValue().set(
                "refresh:token:" + creator.getId(),
                rawRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds(),
                TimeUnit.SECONDS
        );

        return new TokenResponse(accessToken, rawRefreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkAuthorization(AuthorizationRequest request, String creatorId) {
        return creatorRepository.existsById(UUID.fromString(creatorId));
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        UUID creatorId;
        try {
            creatorId = jwtProvider.getUserIdFromToken(refreshToken.trim());
        } catch (Exception e) {
            throw new InvalidRefreshTokenException();
        }

        String stored = redisTemplate.opsForValue().get("refresh:token:" + creatorId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtProvider.generateAccessToken(creatorId);
        String newRefreshToken = jwtProvider.generateRefreshToken(creatorId);
        redisTemplate.opsForValue().set(
                "refresh:token:" + creatorId,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds(),
                TimeUnit.SECONDS
        );

        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Override
    public void logout(String creatorId) {
        redisTemplate.delete("refresh:token:" + creatorId);
    }
}