package com.example.settlementservice.presentation.web;

import com.example.settlementservice.application.exception.*;
import com.example.settlementservice.domain.settlement.InvalidSettlementStateException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SettlementNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSettlementNotFound(SettlementNotFoundException ex) {
        return new ErrorResponse("SETTLEMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWalletNotFound(WalletNotFoundException ex) {
        return new ErrorResponse("WALLET_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CreatorPayoutAccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCreatorPayoutNotFound(CreatorPayoutAccountNotFoundException ex) {
        return new ErrorResponse("CREATOR_PAYOUT_ACCOUNT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateIdempotencyKey(DuplicateIdempotencyKeyException ex) {
        return new ErrorResponse("DUPLICATE_IDEMPOTENCY_KEY", ex.getMessage());
    }

    @ExceptionHandler(InvalidSettlementStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidSettlementState(InvalidSettlementStateException ex) {
        return new ErrorResponse("INVALID_SETTLEMENT_STATE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("INVALID_ARGUMENT",
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
    }

    public record ErrorResponse(String code, String message) {}
}