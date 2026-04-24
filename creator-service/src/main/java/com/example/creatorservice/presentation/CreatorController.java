package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.CreatorUseCase;
import com.example.creatorservice.presentation.dto.req.AuthorizationRequest;
import com.example.creatorservice.presentation.dto.req.JoinRequest;
import com.example.creatorservice.presentation.dto.req.LoginRequest;
import com.example.creatorservice.presentation.dto.res.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Creator", description = "크리에이터 API")
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorUseCase creatorUseCase;

    @Operation(summary = "회원가입", description = "크리에이터 계정을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = UUID.class))),
            @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복", content = @Content)
    })
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UUID> save(@RequestBody JoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creatorUseCase.join(request));
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일 중복 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용 가능한 이메일"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일", content = @Content)
    })
    @GetMapping("/email/check")
    public ResponseEntity<Void> checkEmail(
            @Parameter(description = "확인할 이메일 주소", example = "creator@example.com", required = true)
            @RequestParam String email) {
        creatorUseCase.checkEmailDuplicate(email);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 중복 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용 가능한 닉네임"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임", content = @Content)
    })
    @GetMapping("/nickname/check")
    public ResponseEntity<Void> checkNickname(
            @Parameter(description = "확인할 닉네임", example = "멋진크리에이터", required = true)
            @RequestParam String nickname) {
        creatorUseCase.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse response = creatorUseCase.login(request);

        return ResponseEntity.ok().body(response);
    }

    @Operation(summary = "권한 확인", description = "크리에이터의 특정 리소스 접근 권한을 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "권한 확인 결과 반환 (true/false)"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @GetMapping("/authorization/check")
    public ResponseEntity<Boolean> check(
            @ModelAttribute AuthorizationRequest request,
            @Parameter(description = "크리에이터 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-Creator-Id") String creatorId) {
        return ResponseEntity.ok(creatorUseCase.checkAuthorization(request, creatorId));
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
        return ResponseEntity.status(HttpStatus.CREATED).body(creatorUseCase.refresh(refreshToken));
    }

    @Operation(summary = "로그아웃", description = "크리에이터 로그아웃 및 리프레시 토큰을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "크리에이터 ID (게이트웨이에서 주입)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("X-Creator-Id") String creatorId) {
        creatorUseCase.logout(creatorId);
        return ResponseEntity.ok().build();
    }






//    @GetMapping("/{creatorId}")
//    public CreatorResponse getCreator(@PathVariable UUID creatorId) {
//        return getCreatorUseCase.getCreator(creatorId);
//    }
//
//    @DeleteMapping("/{creatorId}")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
//    public void deleteCreator(@PathVariable UUID creatorId) {
//        deleteCreatorUseCase.deleteCreator(creatorId);
//    }
//
//    @PostMapping("/{creatorId}/account")
//    public CreatorResponse registerAccount(
//            @PathVariable UUID creatorId,
//            @RequestBody @Valid RegisterAccountRequest request) {
//        return registerCreatorAccountUseCase.registerAccount(creatorId, request);
//    }
//
//    @PutMapping("/{creatorId}/account")
//    public CreatorResponse updateAccount(
//            @PathVariable UUID creatorId,
//            @RequestBody @Valid UpdateAccountRequest request) {
//        return updateCreatorAccountUseCase.updateAccount(creatorId, request);
//    }
}