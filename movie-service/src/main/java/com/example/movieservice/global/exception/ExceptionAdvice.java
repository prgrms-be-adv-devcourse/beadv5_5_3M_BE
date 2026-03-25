package com.example.movieservice.global.exception;

import com.example.movieservice.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(GeneralException e) {
        var reason = e.getErrorReasonHttpStatus();
        return ResponseEntity
                .status(reason.httpStatus())
                .body(new ApiResponse<>(false, reason.code(), reason.message(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .badRequest()
                .body(new ApiResponse<>(false, "COMMON400", message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .internalServerError()
                .body(new ApiResponse<>(false, "COMMON500", "서버 에러가 발생했습니다", null));
    }
}