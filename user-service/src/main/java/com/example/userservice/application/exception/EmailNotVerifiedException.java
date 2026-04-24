package com.example.userservice.application.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("이메일 인증이 완료되지 않았습니다.");
    }
}
