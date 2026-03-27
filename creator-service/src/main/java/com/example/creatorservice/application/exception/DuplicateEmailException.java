package com.example.creatorservice.application.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("사용 불가능한 이메일입니다.");
    }
}