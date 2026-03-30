package com.example.userservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "프로필 수정 요청 (multipart/form-data)")
public record UpdateProfileRequest(
        @Schema(description = "변경할 닉네임", example = "새닉네임", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty String nickname,

        @Schema(description = "변경할 전화번호", example = "010-9876-5432")
        String phone,

        @Schema(description = "변경할 프로필 이미지 파일")
        MultipartFile profileImage
) {
}
