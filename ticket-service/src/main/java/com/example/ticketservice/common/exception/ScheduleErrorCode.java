package com.example.ticketservice.common.exception;

import org.springframework.http.HttpStatus;

public enum ScheduleErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 스케줄입니다: %s"),
    FULL(HttpStatus.CONFLICT, "티켓이 모두 매진된 스케줄입니다: %s"),
    EXPIRED(HttpStatus.CONFLICT, "예매가 마감된 스케줄입니다: %s"),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 등록된 스케줄입니다: %s"),
    CART_PERIOD_EXPIRED(HttpStatus.CONFLICT, "장바구니 기간이 지난 스케줄입니다: %s");


    private final HttpStatus status;
    private final String messageTemplate;

    ScheduleErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ScheduleException of(Long scheduleId) {
        return new ScheduleException(this, String.format(messageTemplate, scheduleId));
    }
}