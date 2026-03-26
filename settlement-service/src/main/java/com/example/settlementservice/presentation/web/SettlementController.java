package com.example.settlementservice.presentation.web;

import com.example.settlementservice.application.port.in.CancelSettlementUseCase;
import com.example.settlementservice.application.port.in.GetSettlementUseCase;
import com.example.settlementservice.application.port.in.RequestSettlementUseCase;
import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse requestSettlement(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PostSettlementRequest request) {
        return requestSettlementUseCase.requestSettlement(creatorId, idempotencyKey, request);
    }

    @PostMapping("/{id}/cancel")
    public SettlementResponse cancelSettlement(
            @PathVariable Long id,
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return cancelSettlementUseCase.cancelSettlement(id, creatorId);
    }

    @GetMapping("/{id}")
    public SettlementResponse getSettlement(
            @PathVariable Long id,
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return getSettlementUseCase.getSettlement(id, creatorId);
    }

    @GetMapping
    public List<SettlementResponse> getSettlements(
            @RequestHeader("X-Creator-Id") UUID creatorId) {
        return getSettlementUseCase.getSettlementsByCreatorId(creatorId);
    }
}