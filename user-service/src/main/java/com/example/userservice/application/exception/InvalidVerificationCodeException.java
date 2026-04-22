package com.example.userservice.application.exception;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("인증 코드가 유효하지 않거나 만료되었습니다.");
    }
}
