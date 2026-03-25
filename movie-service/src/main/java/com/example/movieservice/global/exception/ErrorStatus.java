package com.example.movieservice.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // Common
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러가 발생했습니다"),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다"),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다"),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "접근 권한이 없습니다"),

    // Creator
    CREATOR_DUPLICATE_PHONE(HttpStatus.CONFLICT, "CREATOR409", "이미 등록된 전화번호입니다"),
    CREATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "CREATOR404", "크리에이터를 찾을 수 없습니다");

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