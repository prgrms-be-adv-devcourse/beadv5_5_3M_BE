package com.example.userservice.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "쿠키 차감 응답")
public record DeductCookieResponse(
        @Schema(description = "유저 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "티켓 ID", example = "1")
        Long ticketId,

        @Schema(description = "차감된 쿠키 수량", example = "5")
        Integer deductCookieAmount,

        @Schema(description = "처리 성공 여부", example = "true")
        boolean flag
) {
}
