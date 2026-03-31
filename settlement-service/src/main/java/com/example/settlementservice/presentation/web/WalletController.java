package com.example.settlementservice.presentation.web;

import com.example.settlementservice.application.port.in.GetWalletBalanceUseCase;
import com.example.settlementservice.presentation.web.dto.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Wallet", description = "지갑 잔액 조회 API")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;

    @Operation(summary = "지갑 잔액 조회", description = "크리에이터의 현재 지갑 잔액을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공 (지갑 미존재 시 잔액 0 반환)")
    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getBalance(
            @Parameter(description = "크리에이터 ID", required = true) @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(getWalletBalanceUseCase.getBalance(creatorId));
    }
}