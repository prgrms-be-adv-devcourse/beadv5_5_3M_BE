package com.example.ticketservice.common.exception;

import org.springframework.http.HttpStatus;

public enum CartItemErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 장바구니 항목입니다: %s"),
    ALREADY_IN_CART(HttpStatus.CONFLICT, "이미 장바구니에 담긴 스케줄입니다: %s"),
    SCHEDULE_EXPIRED(HttpStatus.CONFLICT, "예매가 마감된 스케줄입니다: %s");

    private final HttpStatus status;
    private final String messageTemplate;

    CartItemErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public CartItemException of(Long id) {
        return new CartItemException(this, String.format(messageTemplate, id));
    }
}