package com.example.creatorservice.presentation.dto;

import com.example.creatorservice.domain.model.Creator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "정산 계좌 응답")
public record PayoutAccountResponse(
        @Schema(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
        @Schema(description = "은행명", example = "국민은행")
        String bankName,
        @Schema(description = "계좌번호", example = "123-456-789012")
        String accountNumber,
        @Schema(description = "예금주명", example = "홍길동")
        String accountHolder
) {
    public static PayoutAccountResponse from(Creator creator) {
        return new PayoutAccountResponse(
                creator.getId(),
                creator.getBankName(),
                creator.getAccountNumber(),
                creator.getAccountHolder()
        );
    }
}