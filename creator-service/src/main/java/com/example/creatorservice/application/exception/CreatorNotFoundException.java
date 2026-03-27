package com.example.creatorservice.application.exception;

public class CreatorNotFoundException extends RuntimeException {
    public CreatorNotFoundException() {
        super("존재하지 않는 회원입니다.");
    }
}