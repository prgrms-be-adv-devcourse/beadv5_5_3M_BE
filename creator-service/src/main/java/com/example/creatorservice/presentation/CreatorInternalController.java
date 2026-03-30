package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.CreatorInternalUseCase;
import com.example.creatorservice.presentation.dto.PayoutAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Creator Internal", description = "크리에이터 내부 API (서비스 간 통신 전용)")
@RestController
@RequestMapping("/internal/creators")
@RequiredArgsConstructor
public class CreatorInternalController {

    private final CreatorInternalUseCase creatorInternalUseCase;

    @Operation(summary = "정산 계좌 조회", description = "크리에이터의 정산 계좌 정보를 조회합니다. (내부 서비스 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "계좌 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = PayoutAccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "크리에이터를 찾을 수 없음", content = @Content)
    })
    @GetMapping("/{creatorId}/payout-account")
    public PayoutAccountResponse getPayoutAccount(
            @Parameter(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
            @PathVariable UUID creatorId) {
        return creatorInternalUseCase.getPayoutAccount(creatorId);
    }
}