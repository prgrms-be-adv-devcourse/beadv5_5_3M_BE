package com.example.userservice.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유저 정보 응답")
public record UserInfoResponse(
        @Schema(description = "닉네임", example = "멋진유저")
        String nickname,

        @Schema(description = "쿠키 잔액", example = "10")
        Integer cookieBalance,

        @Schema(description = "이메일 주소", example = "user@example.com")
        String email,

        @Schema(description = "프로필 이미지 URL", example = "https://storage.example.com/profiles/uuid.jpg")
        String profileUrl,

        @Schema(description = "전화번호", example = "010-1234-5678")
        String phone
) {
}
