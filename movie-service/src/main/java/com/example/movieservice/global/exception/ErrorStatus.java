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
    MOVIE_ALREADY_SCHEDULED(HttpStatus.BAD_REQUEST, "MOVIE_ALREADY_SCHEDULED", "이미 확정된 스케줄이 존재합니다"),

    // Category
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다"),

    // Schedule
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "스케줄을 찾을 수 없습니다"),
    SCHEDULE_INVALID_CREATOR(HttpStatus.FORBIDDEN, "SCHEDULE_INVALID_CREATOR", "해당 스케줄에 대한 권한이 없습니다"),
    SCHEDULE_ALREADY_CONFIRMED(HttpStatus.BAD_REQUEST, "SCHEDULE_ALREADY_CONFIRMED", "이미 확정된 스케줄은 수정하거나 삭제할 수 없습니다"),
    SCHEDULE_NO_REMAINING_SEATS(HttpStatus.BAD_REQUEST, "SCHEDULE_NO_REMAINING_SEATS", "남은 좌석이 없습니다"),
    REQUEST_TIME_CONFLICT(HttpStatus.CONFLICT, "REQUEST_TIME_CONFLICT", "요청한 스케줄들의 일정이 겹칩니다"),
    SCHEDULE_TIME_CONFLICT(HttpStatus.CONFLICT, "SCHEDULE_TIME_CONFLICT", "해당 시간에 이미 편성된 일정이 있습니다"),
    INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_TIME", "상영 시간은 정각이어야 합니다"),

    // Kafka
    KAFKA_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILED", "Kafka 메시지 발행에 실패했습니다"),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다"),
    ;

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