package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.response.CartItemResponse;
import com.example.ticketservice.application.usecase.CartUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Cart", description = "장바구니 추가/제거/조회 API")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartUseCase cartUseCase;

    @Operation(summary = "장바구니 추가", description = "스케줄을 장바구니에 담습니다. 스케줄이 CART 상태일 때만 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "404", description = "스케줄 없음"),
            @ApiResponse(responseCode = "409", description = "장바구니 기간 아님 또는 이미 담긴 스케줄")
    })
    @PostMapping("/{scheduleId}")
    public ResponseEntity<Void> addToCart(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long scheduleId) {
        cartUseCase.addToCart(userId, scheduleId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "장바구니 제거", description = "장바구니에서 스케줄을 제거합니다.")
    @ApiResponse(responseCode = "204", description = "제거 성공")
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> removeFromCart(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long scheduleId) {
        cartUseCase.removeFromCart(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 장바구니 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getMyCart(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cartUseCase.getMyCart(userId));
    }

    @Operation(summary = "장바구니 수요 조회", description = "특정 스케줄의 장바구니 담긴 수를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{scheduleId}/count")
    public ResponseEntity<Long> getCartCount(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long scheduleId) {
        return ResponseEntity.ok(cartUseCase.getCartCount(scheduleId));
    }
}