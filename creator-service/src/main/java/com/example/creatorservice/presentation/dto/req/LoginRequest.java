package com.example.creatorservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크리에이터 로그인 요청")
public record LoginRequest(
        @Schema(description = "이메일 주소", example = "creator@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "비밀번호", example = "password1234!", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
