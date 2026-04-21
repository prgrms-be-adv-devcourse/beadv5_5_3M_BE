package com.example.userservice.presentation;

import com.example.userservice.application.usecase.UserUseCase;
import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import com.example.userservice.presentation.dto.req.LoginRequest;
import com.example.userservice.presentation.dto.req.UpdateProfileRequest;
import com.example.userservice.presentation.dto.res.TokenResponse;
import com.example.userservice.presentation.dto.res.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.userservice.presentation.dto.req.OAuthLoginRequest;
import java.util.UUID;

@Tag(name = "User", description = "유저 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserUseCase userUseCase;

    @Operation(summary = "권한 확인", description = "유저의 특정 리소스 접근 권한을 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "권한 확인 결과 반환 (true/false)"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @GetMapping("/authorization/check")
    public ResponseEntity<Boolean> check(
            @ModelAttribute AuthorizationRequest request,
            @Parameter(description = "유저 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "액세스 토큰 (게이트웨이에서 주입)")
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        return ResponseEntity.ok(userUseCase.checkAuthorization(request, userId, accessToken));
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일 중복 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용 가능한 이메일"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일", content = @Content)
    })
    @GetMapping("/email/check")
    public ResponseEntity<Void> checkEmail(
            @Parameter(description = "확인할 이메일 주소", example = "user@example.com", required = true)
            @RequestParam String email) {
        userUseCase.checkEmailDuplicate(email);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 중복 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용 가능한 닉네임"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임", content = @Content)
    })
    @GetMapping("/nickname/check")
    public ResponseEntity<Void> checkNickname(
            @Parameter(description = "확인할 닉네임", example = "멋진유저", required = true)
            @RequestParam String nickname) {
        userUseCase.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원가입", description = "유저 계정을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = UUID.class))),
            @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복", content = @Content)
    })
    @PostMapping("/join")
    public ResponseEntity<UUID> join(@Valid @RequestBody JoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userUseCase.join(request));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = userUseCase.login(request);
        return ResponseEntity.ok().body(response);
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새로운 액세스 토큰을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "토큰 갱신 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 리프레시 토큰", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(description = "리프레시 토큰", required = true)
            @RequestBody String refreshToken) {
        TokenResponse response = userUseCase.refresh(refreshToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 무효화하고 로그아웃합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "유저 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-User-Id") String userId) {
        userUseCase.logout(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @Parameter(description = "유저 ID (게이트웨이에서 주입)", required = true)
            @RequestHeader("X-User-Id") String userId) {
        userUseCase.withdraw(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인된 유저의 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = UserInfoResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(
            @Parameter(description = "유저 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userUseCase.me(userId));
    }

    @Operation(summary = "Google OAuth 로그인", description = "Google 인가 코드로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "500", description = "Google OAuth 처리 실패", content = @Content)
    })
    @PostMapping("/oauth2/google")
    public ResponseEntity<TokenResponse> oauthGoogle(@Valid @RequestBody OAuthLoginRequest request) {
        TokenResponse response = userUseCase.oauthLogin(request.code());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "프로필 수정", description = "닉네임, 전화번호, 프로필 이미지를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content),
            @ApiResponse(responseCode = "500", description = "이미지 업로드 실패", content = @Content)
    })
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfile(
            @ModelAttribute UpdateProfileRequest request,
            @Parameter(description = "유저 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-User-Id") String userId) {
        userUseCase.updateProfile(userId, request.nickname(), request.phone(), request.profileImage());
        return ResponseEntity.ok().build();
    }
}
