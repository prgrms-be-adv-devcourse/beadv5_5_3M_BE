package com.example.userservice.application;

import com.example.userservice.domain.model.Permission;
import com.example.userservice.domain.model.RefreshToken;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.repository.PermissionRepository;
import com.example.userservice.domain.repository.RefreshTokenRepository;
import com.example.userservice.domain.repository.UserRepository;
import com.example.userservice.exception.DuplicateEmailException;
import com.example.userservice.exception.DuplicateNicknameException;
import com.example.userservice.exception.InvalidEmailOrPasswordException;
import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import com.example.userservice.presentation.dto.req.LoginRequest;
import com.example.userservice.presentation.dto.res.LoginResponse;
import com.example.userservice.presentation.dto.res.TokenResponse;
import com.example.userservice.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    @Override
    public boolean checkAuthorization(AuthorizationRequest request, String userId) {
        User findUser = userRepository.findById(toUUID(userId));
        List<Permission> permissions = permissionRepository.findByRole(findUser.getRole());

        return permissions.stream()
                .filter(p -> p.getHttpMethod() == null || p.getHttpMethod().equals(request.httpMethod().name()))
                .anyMatch(p -> request.requestPath().startsWith(p.getPathPattern()));
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
    public UUID join(JoinRequest request) {
        checkEmailDuplicate(request.email());
        checkNicknameDuplicate(request.nickname());

        User user = User.create(request.email(), request.password(), request.nickname());
        userRepository.save(user);

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
        refreshTokenRepository.save(RefreshToken.create(user.getUserId().toString(), rawRefreshToken, jwtProvider.getRefreshTokenExpirySeconds()));

        return new TokenResponse(accessToken, rawRefreshToken);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        UUID userId;
        try {
            userId = jwtProvider.getUserIdFromToken(refreshToken);
        } catch (Exception e) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!stored.getToken().equals(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        refreshTokenRepository.save(RefreshToken.create(userId.toString(), newRefreshToken, jwtProvider.getRefreshTokenExpirySeconds()));

        return new TokenResponse(accessToken, newRefreshToken);
    }

    private UUID toUUID(String userId) {
        return UUID.fromString(userId);
    }
}
