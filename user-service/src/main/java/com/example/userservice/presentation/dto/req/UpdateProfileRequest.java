package com.example.userservice.presentation.dto.req;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.multipart.MultipartFile;

public record UpdateProfileRequest(
        @NotEmpty String nickname,
        String phone,
        MultipartFile profileImage
) {
}
