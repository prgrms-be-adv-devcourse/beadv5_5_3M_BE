package com.example.userservice.exception;

public class DuplicateNicknameException extends RuntimeException {
    public DuplicateNicknameException() {
        super("사용 불가능한 닉네임입니다.");
    }
}
