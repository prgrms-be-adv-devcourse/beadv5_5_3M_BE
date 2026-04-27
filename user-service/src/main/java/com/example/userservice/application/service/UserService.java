package com.example.userservice.application.service;

import com.example.userservice.application.dto.GoogleUserInfo;
import com.example.userservice.application.exception.*;
import com.example.userservice.application.usecase.UserUseCase;
import com.example.userservice.domain.model.CookieLog;
import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.Wallet;
import com.example.userservice.domain.model.DeletedUser;
import com.example.userservice.domain.repository.CookieLogRepository;
import com.example.userservice.domain.repository.DeletedUserRepository;
import com.example.userservice.domain.repository.PermissionRepository;
import com.example.userservice.domain.repository.UserRepository;
import com.example.userservice.domain.repository.WalletRepository;

import com.example.userservice.infrastructure.kafka.event.UserCreatedEvent;
import com.example.userservice.infrastructure.kafka.event.UserDeletedEvent;

import com.example.userservice.application.port.RedisPort;
import java.security.SecureRandom;

import com.example.userservice.infrastructure.kafka.event.UserUpdatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
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
    private final RedisPort redisPort;
    private final ApplicationEventPublisher eventPublisher;
    private final CookieLogRepository cookieLogRepository;
    private final DeletedUserRepository deletedUserRepository;
    private final GoogleOAuthService googleOAuthService;
    private final JavaMailSender mailSender;

    @Override
    public boolean checkAuthorization(AuthorizationRequest request, String userId, String accessToken) {
        Optional<String> storedToken = redisPort.findAccessToken(userId);
        if (storedToken.isEmpty() || !storedToken.get().equals(accessToken)) {
            throw new SessionExpiredException();
        }

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
    public void sendVerificationCode(String email) {
        checkEmailDuplicate(email);

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        redisPort.saveEmailVerificationCode(email, code);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[BEAD] 이메일 인증 코드");
        message.setText("인증 코드: " + code + "\n\n해당 코드는 5분간 유효합니다.");
        mailSender.send(message);
    }

    @Override
    public void verifyEmailCode(String email, String code) {
        String stored = redisPort.findEmailVerificationCode(email).orElse(null);
        if (stored == null || !stored.equals(code)) {
            throw new InvalidVerificationCodeException();
        }
        redisPort.deleteEmailVerificationCode(email);
        redisPort.saveEmailVerified(email);
    }

    @Override
    @Transactional
    public UUID join(JoinRequest request) {
        if (!redisPort.isEmailVerified(request.email())) {
            throw new EmailNotVerifiedException();
        }

        checkEmailDuplicate(request.email());
        checkNicknameDuplicate(request.nickname());

        User user = User.create(request.email(), request.password(), request.nickname(), request.ageGroup(), request.gender());
        user.initWallet();
        userRepository.save(user);

        eventPublisher.publishEvent(UserCreatedEvent.from(user));

        return user.getUserId();
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email());

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(request.password() + user.getSaltKey(), user.getPassword())) {
            throw new InvalidEmailOrPasswordException();
        }

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String rawRefreshToken = jwtProvider.generateRefreshToken(user.getUserId());

        redisPort.saveAccessToken(
                user.getUserId().toString(),
                accessToken,
                jwtProvider.getAccessTokenExpirySeconds()
        );
        redisPort.saveRefreshToken(
                user.getUserId().toString(),
                rawRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds()
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

        String storedToken = redisPort.findRefreshToken(userId.toString()).orElse(null);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        redisPort.saveAccessToken(
                userId.toString(),
                accessToken,
                jwtProvider.getAccessTokenExpirySeconds()
        );
        redisPort.saveRefreshToken(
                userId.toString(),
                newRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds()
        );

        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Override
    public UserInfoResponse me(String userId) {
        User user = userRepository.findById(toUUID(userId));
        String profileUrl = redisPort.findProfileImageUrl(userId).orElse(user.getProfileUrl());
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
            redisPort.saveProfileImageUrl(userId, profileUrl);
        }
        user.updateProfile(nickname, phone, profileUrl);

        eventPublisher.publishEvent(UserUpdatedEvent.from(user));
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

    @Override
    @Transactional
    public TokenResponse oauthLogin(String code) {
        GoogleUserInfo googleUser = googleOAuthService.exchangeCodeForUserInfo(code);

        User user;
        if (userRepository.existsByEmail(googleUser.email())) {
            user = userRepository.findByEmail(googleUser.email());
        } else {
            user = User.create(googleUser.email(), UUID.randomUUID().toString(), googleUser.name(), null, null);
            user.initWallet();
            userRepository.save(user);
            log.info("Google OAuth new user created: email={}", googleUser.email());
            eventPublisher.publishEvent(UserCreatedEvent.from(user));
        }

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String rawRefreshToken = jwtProvider.generateRefreshToken(user.getUserId());

        redisPort.saveAccessToken(
                user.getUserId().toString(),
                accessToken,
                jwtProvider.getAccessTokenExpirySeconds()
        );
        redisPort.saveRefreshToken(
                user.getUserId().toString(),
                rawRefreshToken,
                jwtProvider.getRefreshTokenExpirySeconds()
        );

        return new TokenResponse(accessToken, rawRefreshToken);
    }

    @Transactional
    protected User findOrCreateOAuthUser(GoogleUserInfo googleUser) {
        if (userRepository.existsByEmail(googleUser.email())) {
            return userRepository.findByEmail(googleUser.email());
        }
        User user = User.create(googleUser.email(), UUID.randomUUID().toString(), googleUser.name(), null, null);
        user.initWallet();
        userRepository.save(user);
        log.info("Google OAuth new user created: email={}", googleUser.email());
        eventPublisher.publishEvent(UserCreatedEvent.from(user));
        return user;
    }


    private UUID toUUID(String userId) {
        return UUID.fromString(userId);
    }

    @Override
    public void logout(String userId) {
        redisPort.deleteRefreshToken(userId);
    }

    @Override
    @Transactional
    public void withdraw(String userId) {
        UUID uuid = toUUID(userId);
        User user = userRepository.findById(uuid);
        deletedUserRepository.save(DeletedUser.create(uuid));
        userRepository.delete(user);
        eventPublisher.publishEvent(UserDeletedEvent.from(uuid));
    }
}
