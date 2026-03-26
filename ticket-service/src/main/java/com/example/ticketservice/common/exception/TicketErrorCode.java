package com.example.ticketservice.common.exception;

import org.springframework.http.HttpStatus;

public enum TicketErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 티켓입니다: %s"),
    ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예매된 티켓입니다: %s"),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 티켓입니다: %s"),
    ALREADY_HOLD(HttpStatus.CONFLICT, "이미 판매 중지된 티켓입니다: %s"),
    NOT_RESERVED(HttpStatus.CONFLICT, "예매 상태가 아닌 티켓입니다: %s");

    private final HttpStatus status;
    private final String messageTemplate;

    TicketErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public TicketException of(Long ticketId) {
        return new TicketException(this, String.format(messageTemplate, ticketId));
    }
}