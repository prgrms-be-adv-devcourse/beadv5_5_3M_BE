package com.example.settlementservice.application.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("Duplicate idempotency key: " + idempotencyKey);
    }
}