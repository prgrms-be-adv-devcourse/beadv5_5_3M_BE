package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.domain.model.Schedule;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ScheduleRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService implements ScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final MovieRepository movieRepository;

    @Override
    public List<ScheduleForUserResponse> getSpecificMovieSchedule(Long movieId) {
        log.info("[getSpecificMovieSchedule] movieId={}", movieId);
        movieRepository.findByMovieId(movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        List<Schedule> schedules = scheduleRepository.findUpcomingByMovieId(movieId, LocalDateTime.now());
        log.info("[getSpecificMovieSchedule] 조회된 스케줄 수={}", schedules.size());

        return schedules.stream()
                .map(s -> new ScheduleForUserResponse(s.getScheduleId(), s.getTicketingTime(), s.getStartTime(), s.getRemainingSeats(), s.getStatus().name()))
                .toList();
    }

    @Override
    @Transactional
    public void decreaseSeat(Long scheduleId) {
        Schedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SCHEDULE_NOT_FOUND));
        if (schedule.getRemainingSeats() <= 0) {
            throw new GeneralException(ErrorStatus.SCHEDULE_NO_REMAINING_SEATS);
        }
        schedule.decreaseRemainingSeats();
    }

    @Override
    @Transactional
    public void increaseSeat(Long scheduleId) {
        Schedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SCHEDULE_NOT_FOUND));
        schedule.increaseRemainingSeats();
    }
}