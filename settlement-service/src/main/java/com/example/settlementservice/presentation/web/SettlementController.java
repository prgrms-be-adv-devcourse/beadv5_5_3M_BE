package com.example.settlementservice.presentation.web;

import com.example.settlementservice.application.port.in.CancelSettlementUseCase;
import com.example.settlementservice.application.port.in.GetSettlementUseCase;
import com.example.settlementservice.application.port.in.RequestSettlementUseCase;
import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settlement", description = "정산 신청/조회/취소 API")
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final RequestSettlementUseCase requestSettlementUseCase;
    private final CancelSettlementUseCase cancelSettlementUseCase;
    private final GetSettlementUseCase getSettlementUseCase;

    @Operation(summary = "정산 신청", description = "크리에이터가 정산을 신청합니다. 동일한 Idempotency-Key로 중복 요청 시 기존 정산을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "정산 신청 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (금액 누락 또는 음수)"),
            @ApiResponse(responseCode = "409", description = "이미 진행 중인 정산 존재")
    })
    @PostMapping
    public ResponseEntity<SettlementResponse> requestSettlement(
            @Parameter(description = "크리에이터 ID", required = true) @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "멱등성 키 (UUID 권장)", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PostSettlementRequest request) {
        return ResponseEntity.status(201)
                .body(requestSettlementUseCase.requestSettlement(creatorId, idempotencyKey, request));
    }

    @Operation(summary = "정산 취소", description = "REQUESTED 상태인 정산을 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "404", description = "정산 미존재"),
            @ApiResponse(responseCode = "409", description = "취소 불가능한 상태")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SettlementResponse> cancelSettlement(
            @Parameter(description = "정산 ID", required = true) @PathVariable Long id,
            @Parameter(description = "크리에이터 ID", required = true) @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(cancelSettlementUseCase.cancelSettlement(id, creatorId));
    }

    @Operation(summary = "정산 단건 조회", description = "정산 ID로 정산 내역을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "정산 미존재")
    })
    @GetMapping("/{id}")
    public ResponseEntity<SettlementResponse> getSettlement(
            @Parameter(description = "정산 ID", required = true) @PathVariable Long id,
            @Parameter(description = "크리에이터 ID", required = true) @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(getSettlementUseCase.getSettlement(id, creatorId));
    }

    @Operation(summary = "정산 목록 조회", description = "크리에이터의 전체 정산 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getSettlements(
            @Parameter(description = "크리에이터 ID", required = true) @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(getSettlementUseCase.getSettlementsByCreatorId(creatorId));
    }
}