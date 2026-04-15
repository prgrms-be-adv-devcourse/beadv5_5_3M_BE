package com.example.userservice.application.service;

import com.example.userservice.application.usecase.UserUseCase;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.Wallet;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.PermissionRepository;
import com.example.userservice.domain.repository.UserRepository;
import com.example.userservice.domain.repository.WalletRepository;

import com.example.userservice.infrastructure.kafka.event.UserCreatedEvent;
import com.example.userservice.infrastructure.kafka.event.UserUpdatedEvent;
import com.example.userservice.application.exception.DuplicateEmailException;
import com.example.userservice.application.exception.DuplicateNicknameException;
import com.example.userservice.application.exception.InvalidEmailOrPasswordException;
import com.example.userservice.application.exception.InvalidRefreshTokenException;

import java.util.concurrent.TimeUnit;

import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.DeductCookieRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import com.example.userservice.presentation.dto.req.LoginRequest;
import com.example.userservice.presentation.dto.req.RefundCookieRequest;
import com.example.userservice.presentation.dto.res.DeductCookieResponse;
import com.example.userservice.presentation.dto.res.RefundCookieResponse;
import com.example.userservice.presentation.dto.res.TokenResponse;
import com.example.userservice.presentation.dto.res.UserInfoResponse;

import com.example.userservice.global.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PermissionRepository permissionRepository;
    private final JwtProvider jwtProvider;
    private final StorageService storageService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CookieLogRepository cookieLogRepository;

    @Override
    public boolean checkAuthorization(AuthorizationRequest request, String userId) {
        User findUser = userRepository.findById(toUUID(userId));
        List<Permission> permissions = permissionRepository.findByRole(findUser.getRole());

        return permissions.stream()
                .filter(p -> p.getHttpMethod() == null || p.getHttpMethod().equals(request.httpMethod().name()))
                .noneMatch(p -> request.requestPath().startsWith(p.getPathPattern()));
    }

    @Override
    public void checkEmailDuplicate(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
    }

    @Override
    public void checkNicknameDuplicate(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new DuplicateNicknameException();
        }
    }

    @Override
    @Transactional
    public UUID join(JoinRequest request) {
        checkEmailDuplicate(request.email());
        checkNicknameDuplicate(request.nickname());

        User user = User.create(request.email(), request.password(), request.nickname());
        userRepository.save(user);
        walletRepository.save(Wallet.create(user));

//        UserCreatedEvent userCreatedEvent = UserCreatedEvent.from(user);
        kafkaTemplate.send("user.created", toJsonString(UserCreatedEvent.from(user)));

        return user.getUserId();
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email());

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(request.password() + user.getSaltKey(), user.getPassword())) {
            throw new InvalidEmailOrPasswordException();
        }

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String rawRefreshToken = jwtProvider.generateRefreshToken(user.getUserId());
        redisTemplate.opsForValue().set(
                "refresh:token:" + user.getUserId(),
                rawRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds(),
                TimeUnit.SECONDS
        );

        return new TokenResponse(accessToken, rawRefreshToken);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        UUID userId;
        try {
            userId = jwtProvider.getUserIdFromToken(refreshToken.trim());
        } catch (Exception e) {
            log.error("refresh token parse 실패: {}", e.getMessage(), e);
            throw new InvalidRefreshTokenException();
        }

        String storedToken = redisTemplate.opsForValue().get("refresh:token:" + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        redisTemplate.opsForValue().set(
                "refresh:token:" + userId,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds(),
                TimeUnit.SECONDS
        );

        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Override
    public UserInfoResponse me(String userId) {
        User user = userRepository.findById(toUUID(userId));
        String profileUrl = redisTemplate.opsForValue().get("profile:image:" + userId);
        if (profileUrl == null) {
            profileUrl = user.getProfileUrl();
        }
        return new UserInfoResponse(user.getNickname(), user.getBalance(), user.getEmail(), profileUrl, user.getPhone());
    }

    @Override
    @Transactional
    public void updateProfile(String userId, String nickname, String phone, MultipartFile profileImage) {
        User user = userRepository.findById(toUUID(userId));
        if (nickname != null && !nickname.equals(user.getNickname())) {
            checkNicknameDuplicate(nickname);
        }
        String profileUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            profileUrl = storageService.upload(profileImage, userId);
            redisTemplate.opsForValue().set("profile:image:" + userId, profileUrl);
        }
        user.updateProfile(nickname, phone, profileUrl);

        UserUpdatedEvent userUpdatedEvent = UserUpdatedEvent.from(user);
        kafkaTemplate.send("user.updated", toJsonString(userUpdatedEvent));
    }

    @Override
    @Transactional
    public DeductCookieResponse deductCookie(DeductCookieRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.userId());
        wallet.deduct(request.amount());
        CookieLog cookieLog = CookieLog.create(request.userId(), request.amount(), request.ticketId());
        cookieLogRepository.save(cookieLog);
        return new DeductCookieResponse(request.userId(), request.ticketId(), request.amount(), true);
    }

    @Override
    @Transactional
    public RefundCookieResponse refundCookie(RefundCookieRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.userId());
        wallet.add(request.amount());
        CookieLog cookieLog = CookieLog.create(request.userId(), request.amount(), request.ticketId());
        cookieLogRepository.save(cookieLog);
        return new RefundCookieResponse(request.userId(), request.ticketId(), request.amount(), true);
    }

    private UUID toUUID(String userId) {
        return UUID.fromString(userId);
    }

    @Override
    public void logout(String userId) {
        redisTemplate.delete("refresh:token:" + userId);
    }

    private String toJsonString(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Json 직렬화 실패");
        }
    }
}
