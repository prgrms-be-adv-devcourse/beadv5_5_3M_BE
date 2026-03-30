package com.example.creatorservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크리에이터 회원가입 요청")
public record JoinRequest(
        @Schema(description = "이메일 주소", example = "creator@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "비밀번호", example = "password1234!", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,
        @Schema(description = "전화번호", example = "010-1234-5678")
        String phoneNumber,
        @Schema(description = "은행명", example = "국민은행")
        String bankName,
        @Schema(description = "계좌번호", example = "123-456-789012")
        String accountNumber,
        @Schema(description = "예금주명", example = "홍길동")
        String accountHolder,
        @Schema(description = "닉네임", example = "멋진크리에이터", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {
}
