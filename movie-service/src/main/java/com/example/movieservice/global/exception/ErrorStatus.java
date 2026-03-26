package com.example.movieservice.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_SERVER_ERROR", "서버 에러가 발생했습니다"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_BAD_REQUEST", "잘못된 요청입니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_FORBIDDEN", "접근 권한이 없습니다"),

    // Movie
    MOVIE_REGISTRATION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "MOVIE_REGISTRATION_LIMIT_EXCEEDED", "영화는 최대 3개까지 등록할 수 있습니다"),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND", "영화를 찾을 수 없습니다"),
    MOVIE_INVALID_CREATOR(HttpStatus.FORBIDDEN, "MOVIE_INVALID_CREATOR", "해당 영화에 대한 권한이 없습니다"),
    MOVIE_NOT_PUBLIC(HttpStatus.NOT_FOUND, "MOVIE_NOT_PUBLIC", "비공개 영화입니다"),

    // Category
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDto getReason() {
        return new ErrorReasonDto(null, code, message);
    }

    @Override
    public ErrorReasonDto getReasonHttpStatus() {
        return new ErrorReasonDto(httpStatus, code, message);
    }
}