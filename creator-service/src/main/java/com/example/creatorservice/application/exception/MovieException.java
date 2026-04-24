package com.example.creatorservice.application.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MovieException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MovieException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static MovieException notFound() {
        return new MovieException(HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND", "영화를 찾을 수 없습니다.");
    }

    public static MovieException forbidden() {
        return new MovieException(HttpStatus.FORBIDDEN, "MOVIE_FORBIDDEN", "해당 영화에 대한 권한이 없습니다.");
    }

    public static MovieException notPublic() {
        return new MovieException(HttpStatus.NOT_FOUND, "MOVIE_NOT_PUBLIC", "공개된 영화가 아닙니다.");
    }

    public static MovieException registrationLimitExceeded() {
        return new MovieException(HttpStatus.CONFLICT, "MOVIE_REGISTRATION_LIMIT_EXCEEDED", "영화는 최대 3편까지 등록할 수 있습니다.");
    }

    public static MovieException alreadyScheduled() {
        return new MovieException(HttpStatus.CONFLICT, "MOVIE_ALREADY_SCHEDULED", "확정된 상영 일정이 있어 수정/삭제할 수 없습니다.");
    }

    public static MovieException invalidVideoFormat(String detail) {
        return new MovieException(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_FORMAT", detail);
    }

    public static MovieException categoryNotFound(Long categoryId) {
        return new MovieException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다: " + categoryId);
    }
}