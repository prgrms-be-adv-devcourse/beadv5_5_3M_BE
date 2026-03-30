package com.example.userservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "유저 회원가입 요청")
public record JoinRequest(
        @Schema(description = "이메일 주소", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "password1234!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password,

        @Schema(description = "닉네임", example = "멋진유저", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String nickname
) {
}
