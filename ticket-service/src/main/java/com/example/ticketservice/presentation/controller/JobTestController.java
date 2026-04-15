package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.usecase.CartCloseUseCase;
import com.example.ticketservice.application.usecase.ReviewAuthUseCase;
import com.example.ticketservice.application.usecase.TicketingStartUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job Test (dev only)", description = "Quartz Job 수동 트리거 — dev 환경 전용")
@Profile("dev")
@RestController
@RequestMapping("/internal/test/jobs")
@RequiredArgsConstructor
public class JobTestController {

    private final CartCloseUseCase cartCloseUseCase;
    private final TicketingStartUseCase ticketingStartUseCase;
    private final ReviewAuthUseCase reviewAuthUseCase;

    @Operation(summary = "장바구니 마감 트리거",
            description = "CartCloseQuartzJob 수동 실행 — Case A/B 분기, CART → IN_PROGRESSING")
    @PostMapping("/cart-close/{scheduleId}")
    public ResponseEntity<Void> cartClose(@PathVariable Long scheduleId) {
        cartCloseUseCase.execute(scheduleId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "티켓팅 시작 트리거",
            description = "TicketingStartQuartzJob 수동 실행 — 미결제 회수, Redis stock 초기화, IN_PROGRESSING → TICKETING")
    @PostMapping("/ticketing-start/{scheduleId}")
    public ResponseEntity<Void> ticketingStart(@PathVariable Long scheduleId) {
        ticketingStartUseCase.execute(scheduleId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리뷰 권한 발행 트리거",
            description = "ReviewAuthQuartzJob 수동 실행 — CONFIRMED 티켓 조회 후 Kafka ticket.review.authorized 발행")
    @PostMapping("/review-auth/{scheduleId}")
    public ResponseEntity<Void> reviewAuth(@PathVariable Long scheduleId) {
        reviewAuthUseCase.publishReviewAuth(scheduleId);
        return ResponseEntity.ok().build();
    }
}