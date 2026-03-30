package com.example.userservice.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "쿠키 차감 요청 (내부 서비스 전용)")
public record DeductCookieRequest(
        @Schema(description = "티켓 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long ticketId,

        @Schema(description = "차감할 쿠키 수량", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer amount,

        @Schema(description = "유저 ID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId
) {
}
