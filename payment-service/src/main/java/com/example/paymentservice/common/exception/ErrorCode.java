package com.example.paymentservice.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다"),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "결제 내역을 찾을 수 없습니다"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "P002", "이미 처리된 결제입니다"),
    PAYMENT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "P003", "결제 금액이 올바르지 않습니다"),
    PG_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "P004", "PG 결제 확인에 실패했습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
