package com.example.settlementservice.presentation.web;

import com.example.settlementservice.application.port.in.CancelSettlementUseCase;
import com.example.settlementservice.application.port.in.GetSettlementUseCase;
import com.example.settlementservice.application.port.in.RequestSettlementUseCase;
import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final RequestSettlementUseCase requestSettlementUseCase;
    private final CancelSettlementUseCase cancelSettlementUseCase;
    private final GetSettlementUseCase getSettlementUseCase;

    @PostMapping
    public ResponseEntity<SettlementResponse> requestSettlement(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PostSettlementRequest request) {
        return ResponseEntity.status(201)
                .body(requestSettlementUseCase.requestSettlement(creatorId, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SettlementResponse> cancelSettlement(
            @PathVariable Long id,
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(cancelSettlementUseCase.cancelSettlement(id, creatorId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementResponse> getSettlement(
            @PathVariable Long id,
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(getSettlementUseCase.getSettlement(id, creatorId));
    }

    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getSettlements(
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return ResponseEntity.ok(getSettlementUseCase.getSettlementsByCreatorId(creatorId));
    }
}