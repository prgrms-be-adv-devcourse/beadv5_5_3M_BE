package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.request.AddCartItemRequest;
import com.example.ticketservice.application.dto.response.CartItemResponse;

import java.util.List;
import java.util.UUID;

public interface CartItemUseCase {

    CartItemResponse addCartItem(UUID userId, AddCartItemRequest request);

    List<CartItemResponse> getCartItems(UUID userId);

    void removeCartItem(UUID userId, Long cartItemId);
}
