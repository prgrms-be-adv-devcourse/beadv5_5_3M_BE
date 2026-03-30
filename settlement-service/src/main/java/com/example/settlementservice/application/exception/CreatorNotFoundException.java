package com.example.settlementservice.application.exception;

import java.util.UUID;

public class CreatorNotFoundException extends RuntimeException {
    public CreatorNotFoundException(UUID creatorId) {
        super("Creator not found: " + creatorId);
    }
}