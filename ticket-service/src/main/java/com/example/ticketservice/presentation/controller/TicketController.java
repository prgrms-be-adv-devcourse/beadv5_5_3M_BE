package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.request.TicketCreateRequest;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.usecase.TicketUseCase;
import com.example.ticketservice.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketUseCase ticketUseCase;

    @PostMapping
    public ResponseEntity<TicketResponse> reserveTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody TicketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketUseCase.reserveTicket(userId, request));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketUseCase.getTicket(userId, ticketId));
    }

    @GetMapping
    public ResponseEntity<PageResult<TicketResponse>> getTicketsByUser(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketUseCase.getTicketsByUser(userId, page, size));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> cancelTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long ticketId
    ) {
        ticketUseCase.cancelTicket(userId, ticketId);
        return ResponseEntity.noContent().build();
    }

}
