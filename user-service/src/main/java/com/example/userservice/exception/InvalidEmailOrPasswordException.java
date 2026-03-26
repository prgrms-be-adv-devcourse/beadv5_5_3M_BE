package com.example.userservice.exception;

public class InvalidEmailOrPasswordException extends RuntimeException {
    public InvalidEmailOrPasswordException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
