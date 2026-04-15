package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.request.TicketCreateRequest;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.usecase.RefundUseCase;
import com.example.ticketservice.application.usecase.SelfPaymentUseCase;
import com.example.ticketservice.application.usecase.TicketUseCase;
import com.example.ticketservice.common.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Ticket", description = "티켓 예약/조회/취소 API")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketUseCase ticketUseCase;
    private final SelfPaymentUseCase selfPaymentUseCase;
    private final RefundUseCase refundUseCase;

    @Operation(summary = "티켓 예약", description = "스케줄 ID로 티켓을 예약합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "예약 성공"),
            @ApiResponse(responseCode = "404", description = "스케줄 없음"),
            @ApiResponse(responseCode = "409", description = "잔여 좌석 없음")
    })
    @PostMapping
    public ResponseEntity<TicketResponse> reserveTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody TicketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketUseCase.reserveTicket(userId, request));
    }

    @Operation(summary = "티켓 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "티켓 없음")
    })
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketUseCase.getTicket(userId, ticketId));
    }

    @Operation(summary = "내 티켓 목록 조회", description = "X-User-Id 기준 페이지네이션 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<PageResult<TicketResponse>> getTicketsByUser(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketUseCase.getTicketsByUser(userId, page, size));
    }

    @Operation(summary = "티켓 취소")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 성공"),
            @ApiResponse(responseCode = "404", description = "티켓 없음"),
            @ApiResponse(responseCode = "409", description = "이미 확정된 티켓")
    })
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> cancelTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId
    ) {
        ticketUseCase.cancelTicket(userId, ticketId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "자율 결제", description = "RESERVED 티켓에 대해 쿠키를 차감하고 CONFIRMED 상태로 전환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 성공"),
            @ApiResponse(responseCode = "402", description = "쿠키 잔액 부족"),
            @ApiResponse(responseCode = "403", description = "본인 티켓 아님"),
            @ApiResponse(responseCode = "404", description = "티켓 없음"),
            @ApiResponse(responseCode = "409", description = "RESERVED 상태 아님")
    })
    @PostMapping("/{ticketId}/pay")
    public ResponseEntity<TicketResponse> payTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId
    ) {
        return ResponseEntity.ok(selfPaymentUseCase.pay(userId, ticketId));
    }

    @Operation(summary = "티켓 환불", description = "CONFIRMED 티켓을 환불하고 쿠키를 복구합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "환불 성공"),
            @ApiResponse(responseCode = "403", description = "본인 티켓 아님"),
            @ApiResponse(responseCode = "404", description = "티켓 없음"),
            @ApiResponse(responseCode = "409", description = "CONFIRMED 상태 아님")
    })
    @PostMapping("/{ticketId}/refund")
    public ResponseEntity<Void> refundTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId
    ) {
        refundUseCase.refund(userId, ticketId);
        return ResponseEntity.noContent().build();
    }

}