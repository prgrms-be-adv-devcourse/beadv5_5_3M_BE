package com.example.ticketservice.common.exception;

import org.springframework.http.HttpStatus;

public enum QueueErrorCode {

    QUEUE_NOT_OPEN(HttpStatus.CONFLICT, "대기열이 열려 있지 않은 스케줄입니다: %s"),
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "이미 대기열에 있는 유저입니다: %s"),
    SOLD_OUT(HttpStatus.CONFLICT, "매진된 스케줄입니다: %s");

    private final HttpStatus status;
    private final String messageTemplate;

    QueueErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public QueueException of(Long scheduleId) {
        return new QueueException(this, String.format(messageTemplate, scheduleId));
    }
}