package com.example.userservice.application.exception;

public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
        super("다른 곳에서 로그인하여 현재 세션이 종료되었습니다.");
    }
}
