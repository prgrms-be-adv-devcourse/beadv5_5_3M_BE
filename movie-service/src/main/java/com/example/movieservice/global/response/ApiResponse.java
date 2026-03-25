package com.example.movieservice.global.response;

import com.example.movieservice.global.exception.BaseErrorCode;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.http.HttpStatus;

@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public record ApiResponse<T>(
        boolean isSuccess,
        String code,
        String message,
        T result
) {
    private static final String SUCCESS_CODE = "200";
    private static final String SUCCESS_MESSAGE = "요청에 성공했습니다";

    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, result);
    }

    public static <T> ApiResponse<T> onSuccess() {
        return new ApiResponse<>(true, Integer.toString(HttpStatus.NO_CONTENT.value()), HttpStatus.NO_CONTENT.getReasonPhrase(), null);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode, T data) {
        var reason = errorCode.getReasonHttpStatus();
        return new ApiResponse<>(false, reason.code(), reason.message(), data);
    }
}