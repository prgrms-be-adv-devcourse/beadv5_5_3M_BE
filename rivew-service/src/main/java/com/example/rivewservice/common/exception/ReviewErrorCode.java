package com.example.rivewservice.common.exception;

import org.springframework.http.HttpStatus;

public enum ReviewErrorCode {

    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다: %s"),
    REVIEW_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "리뷰 작성 권한이 없습니다: %s"),
    REVIEW_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 작성된 리뷰입니다: %s");

    private final HttpStatus status;
    private final String messageTemplate;

    ReviewErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ReviewException of(Long reviewId) {
        return new ReviewException(this, String.format(messageTemplate, reviewId));
    }
}