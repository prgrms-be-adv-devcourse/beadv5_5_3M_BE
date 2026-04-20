package com.example.movieservice.global.exception;

import com.example.movieservice.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(GeneralException e) {
        var reason = e.getErrorReasonHttpStatus();
        return ResponseEntity
                .status(reason.httpStatus())
                .body(new ErrorResponse(reason.code(), reason.message()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        e.getBindingResult().getFieldErrors().forEach(error ->
                log.warn("[Validation] uri: {}, field: {}, message: {}", request.getRequestURI(), error.getField(), error.getDefaultMessage())
        );
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("COMMON400", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[COMMON500] 처리되지 않은 예외 발생", e);
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponse("COMMON500", "서버 에러가 발생했습니다"));
    }
}