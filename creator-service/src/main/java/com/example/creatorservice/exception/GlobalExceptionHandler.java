package com.example.creatorservice.exception;

import com.example.creatorservice.application.exception.CreatorNotFoundException;
import com.example.creatorservice.application.exception.DuplicateEmailException;
import com.example.creatorservice.application.exception.DuplicateNicknameException;
import com.example.creatorservice.application.exception.InvalidEmailOrPasswordException;
import com.example.creatorservice.application.exception.InvalidRefreshTokenException;
import com.example.creatorservice.application.exception.MovieException;
import com.example.creatorservice.application.exception.ScheduleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidEmailOrPasswordException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidEmailOrPassword(InvalidEmailOrPasswordException ex) {
        log.warn("[Auth] 인증 실패 - reason: {}", ex.getClass().getSimpleName());
        return new ErrorResponse("INVALID_EMAIL_OR_PASSWORD", "Invalid credentials");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        log.warn("[Auth] 인증 실패 - reason: {}", ex.getClass().getSimpleName());
        return new ErrorResponse("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token");
    }

    @ExceptionHandler(CreatorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCreatorNotFound(CreatorNotFoundException ex) {
        return new ErrorResponse("CREATOR_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateEmail(DuplicateEmailException ex) {
        return new ErrorResponse("DUPLICATE_EMAIL", ex.getMessage());
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateNickname(DuplicateNicknameException ex) {
        return new ErrorResponse("DUPLICATE_NICKNAME", ex.getMessage());
    }

    @ExceptionHandler(MovieException.class)
    public ResponseEntity<ErrorResponse> handleMovieException(MovieException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ScheduleException.class)
    public ResponseEntity<ErrorResponse> handleScheduleException(ScheduleException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
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