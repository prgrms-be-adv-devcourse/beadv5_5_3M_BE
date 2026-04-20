package com.example.creatorservice.application.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ScheduleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ScheduleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ScheduleException notFound() {
        return new ScheduleException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "스케줄을 찾을 수 없습니다.");
    }

    public static ScheduleException forbidden() {
        return new ScheduleException(HttpStatus.FORBIDDEN, "SCHEDULE_FORBIDDEN", "해당 스케줄에 대한 권한이 없습니다.");
    }

    public static ScheduleException alreadyConfirmed() {
        return new ScheduleException(HttpStatus.BAD_REQUEST, "SCHEDULE_ALREADY_CONFIRMED", "이미 확정된 스케줄은 삭제할 수 없습니다.");
    }

    public static ScheduleException invalidStartTime() {
        return new ScheduleException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_TIME", "상영 시작 시간은 정각(분:00)이어야 합니다.");
    }

    public static ScheduleException requestTimeConflict() {
        return new ScheduleException(HttpStatus.CONFLICT, "REQUEST_TIME_CONFLICT", "요청한 일정들 사이에 시간이 겹칩니다.");
    }

    public static ScheduleException timeConflict() {
        return new ScheduleException(HttpStatus.CONFLICT, "SCHEDULE_TIME_CONFLICT", "이미 확정된 다른 일정과 시간이 겹칩니다.");
    }

    public static ScheduleException invalidTicketingStart() {
        return new ScheduleException(HttpStatus.BAD_REQUEST, "INVALID_TICKETING_START",
                "ticketingTime는 확정 시점 기준 최소 2일 이후여야 합니다.");
    }

    public static ScheduleException invalidTicketingWindow() {
        return new ScheduleException(HttpStatus.BAD_REQUEST, "INVALID_TICKETING_WINDOW",
                "상영 시작 시간과 티켓팅 시작 시간 사이는 최소 10분이어야 합니다.");
    }
}