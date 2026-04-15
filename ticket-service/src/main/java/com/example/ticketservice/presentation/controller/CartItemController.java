package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.request.AddCartItemRequest;
import com.example.ticketservice.application.dto.response.CartItemResponse;
import com.example.ticketservice.application.usecase.CartItemUseCase;
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

@Tag(name = "CartItem", description = "장바구니 API")
@RestController
@RequestMapping("/api/cart-items")
@RequiredArgsConstructor
public class CartItemController {

    private final CartItemUseCase cartItemUseCase;

    @Operation(summary = "장바구니 담기")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "담기 성공"),
            @ApiResponse(responseCode = "404", description = "스케줄 없음"),
            @ApiResponse(responseCode = "409", description = "이미 담긴 스케줄")
    })
    @PostMapping
    public ResponseEntity<CartItemResponse> addCartItem(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartItemUseCase.addCartItem(userId, request));
    }

    @Operation(summary = "장바구니 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCartItems(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cartItemUseCase.getCartItems(userId));
    }

    @Operation(summary = "장바구니 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "장바구니 항목 없음")
    })
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> removeCartItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long cartItemId) {
        cartItemUseCase.removeCartItem(userId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
