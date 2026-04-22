package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.response.QueueEntryResponse;
import com.example.ticketservice.application.dto.response.QueuePositionResponse;
import com.example.ticketservice.application.usecase.QueueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Queue", description = "티켓팅 대기열 / 즉시 구매 API")
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueUseCase queueUseCase;

    @Operation(summary = "티켓팅 진입",
            description = "재고 있으면 즉시 구매(PURCHASED), 없고 결제 중 티켓 있으면 대기열 진입(QUEUED), 둘 다 없으면 매진 에러")
    @PostMapping("/{scheduleId}/enter")
    public ResponseEntity<QueueEntryResponse> enter(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long scheduleId) {
        return ResponseEntity.ok(queueUseCase.enter(userId, scheduleId));
    }

    @Operation(summary = "대기열 순번 조회", description = "0이면 대기열에 없음")
    @GetMapping("/{scheduleId}/position")
    public ResponseEntity<QueuePositionResponse> getPosition(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long scheduleId) {
        return ResponseEntity.ok(queueUseCase.getPosition(userId, scheduleId));
    }
}