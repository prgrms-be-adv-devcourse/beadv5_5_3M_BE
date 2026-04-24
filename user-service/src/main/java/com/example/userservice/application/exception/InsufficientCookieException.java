package com.example.userservice.application.exception;

public class InsufficientCookieException extends RuntimeException {
    public InsufficientCookieException() {
        super("쿠키 잔액이 부족합니다");
    }
}
