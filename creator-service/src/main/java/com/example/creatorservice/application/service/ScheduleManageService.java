package com.example.creatorservice.application.service;

import com.example.creatorservice.application.exception.MovieException;
import com.example.creatorservice.application.exception.ScheduleException;
import com.example.creatorservice.application.usecase.ScheduleManageUseCase;
import com.example.creatorservice.domain.model.Movie;
import com.example.creatorservice.domain.model.Schedule;
import com.example.creatorservice.domain.repository.MovieRepository;
import com.example.creatorservice.domain.repository.ScheduleRepository;
import com.example.creatorservice.infrastructure.kafka.dto.ScheduleConfirmedMessage;
import com.example.creatorservice.infrastructure.kafka.event.ScheduleConfirmedEvent;
import com.example.creatorservice.presentation.dto.req.ConfirmScheduleRequest;
import com.example.creatorservice.presentation.dto.req.RegisterScheduleRequest;
import com.example.creatorservice.presentation.dto.res.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleManageService implements ScheduleManageUseCase {

    private final MovieRepository movieRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public List<Long> register(UUID creatorId, RegisterScheduleRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(MovieException::notFound);

        if (!movie.getCreatorId().equals(creatorId)) {
            throw MovieException.forbidden();
        }

        if (!movie.isPublic()) {
            throw MovieException.notPublic();
        }

        List<RegisterScheduleRequest.ScheduleSlot> slots = request.slots();

        // 요청 내 슬롯들 사이의 시간 충돌 검사
        List<TimeRange> ranges = new ArrayList<>();
        for (RegisterScheduleRequest.ScheduleSlot slot : slots) {
            validateSlot(slot, movie.getRunningTime());
            TimeRange range = TimeRange.of(slot.startTime(), movie.getRunningTime());

            ranges.stream()
                    .filter(existing -> existing.overlaps(range))
                    .findFirst()
                    .ifPresent(conflict -> { throw ScheduleException.requestTimeConflict(); });

            ranges.add(range);
        }

        List<Long> savedIds = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            RegisterScheduleRequest.ScheduleSlot slot = slots.get(i);
            LocalDateTime endTime = ranges.get(i).end();

            // 이미 확정된 일정과의 충돌 검사
            if (scheduleRepository.existsOverlapping(creatorId, slot.startTime(), endTime)) {
                throw ScheduleException.timeConflict();
            }

            Schedule schedule = Schedule.create(slot.ticketingTime(), slot.startTime(), endTime, movie);
            Schedule saved = scheduleRepository.save(schedule);
            savedIds.add(saved.getScheduleId());
            log.info("[Schedule] 스케줄 등록 완료 - scheduleId: {}, movieId: {}", saved.getScheduleId(), movie.getMovieId());
        }

        return savedIds;
    }

    @Override
    @Transactional
    public void confirm(UUID creatorId, ConfirmScheduleRequest request) {
        LocalDateTime now = LocalDateTime.now();

        for (Long scheduleId : request.scheduleIds()) {
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(ScheduleException::notFound);

            if (!schedule.getMovie().getCreatorId().equals(creatorId)) {
                throw ScheduleException.forbidden();
            }

            if (!schedule.getMovie().isPublic()) {
                throw MovieException.notPublic();
            }

            if (schedule.getIsConfirmed()) {
                throw ScheduleException.alreadyConfirmed();
            }

            // 테스트용
            // if (!schedule.getTicketingTime().isAfter(now.plusDays(2))) {
            if (!schedule.getTicketingTime().isAfter(now.plusMinutes(4))) {
                throw ScheduleException.invalidTicketingStart();
            }

            if (scheduleRepository.existsOverlapping(creatorId, schedule.getStartTime(), schedule.getEndTime())) {
                throw ScheduleException.timeConflict();
            }

            schedule.confirm();
            schedule.scheduled();

            Movie movie = schedule.getMovie();
            applicationEventPublisher.publishEvent(new ScheduleConfirmedEvent(new ScheduleConfirmedMessage(
                    schedule.getScheduleId(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getTicketingTime(),
                    movie.getTitle(),
                    movie.getTotalCookie(),
                    movie.getCreatorId(),
                    movie.getMovieId(),
                    movie.getImageUrl(),
                    schedule.getRemainingSeats()
            )));

            log.info("[Schedule] 스케줄 확정 완료 - scheduleId: {}", scheduleId);
        }
    }

    @Override
    @Transactional
    public void delete(UUID creatorId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(ScheduleException::notFound);

        if (!schedule.getMovie().getCreatorId().equals(creatorId)) {
            throw ScheduleException.forbidden();
        }

        if (schedule.getIsConfirmed()) {
            throw ScheduleException.alreadyConfirmed();
        }

        scheduleRepository.delete(schedule);
        log.info("[Schedule] 스케줄 삭제 완료 - scheduleId: {}", scheduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getDraftByDate(UUID creatorId, LocalDate date) {
        return scheduleRepository.findAllByCreatorIdAndDate(creatorId, date).stream()
                .filter(s -> !s.getIsConfirmed())
                .map(ScheduleResponse::from)
                .toList();
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
        static TimeRange of(LocalDateTime start, Integer runningTimeSeconds) {
            return new TimeRange(start, start.plusSeconds(runningTimeSeconds));
        }

        boolean overlaps(TimeRange other) {
            return start.isBefore(other.end) && end.isAfter(other.start);
        }
    }

    private void validateSlot(RegisterScheduleRequest.ScheduleSlot slot, Integer runningTimeSeconds) {
        // 테스트용 정각 등록 제거
//        if (slot.startTime().getMinute() != 0 || slot.startTime().getSecond() != 0) {
//            throw ScheduleException.invalidStartTime();
//        }

        LocalDateTime endTime = slot.startTime().plusSeconds(runningTimeSeconds);
        long minutesBetween = java.time.Duration.between(slot.ticketingTime(), slot.startTime()).toMinutes();

        // 테스트용
        // if (minutesBetween < 10) {
        if (minutesBetween < 4) {
            throw ScheduleException.invalidTicketingWindow();
        }

        if (!slot.ticketingTime().isBefore(slot.startTime())) {
            throw ScheduleException.invalidTicketingWindow();
        }

        // 테스트용
        // if (!slot.ticketingTime().isAfter(LocalDateTime.now().plusDays(2))) {
        if (!slot.ticketingTime().isAfter(LocalDateTime.now().plusMinutes(4))) {
            throw ScheduleException.invalidTicketingStart();
        }
    }
}