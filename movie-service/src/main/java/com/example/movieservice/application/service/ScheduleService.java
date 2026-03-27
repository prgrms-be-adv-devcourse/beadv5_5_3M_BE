package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Schedule;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ScheduleRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.request.RegisterScheduleRequest;
import com.example.movieservice.presentation.dto.request.UpdateConfirmRequest;
import com.example.movieservice.presentation.dto.response.DraftScheduleResponse;
import com.example.movieservice.presentation.dto.response.ScheduleForCreatorResponse;
import com.example.movieservice.presentation.dto.response.ScheduleForUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService implements ScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final MovieRepository movieRepository;

    @Override
    @Transactional
    public void register(UUID creatorId, RegisterScheduleRequest request) {
        // 시작 시간이 정각이 맞는지 더블 체크
        if(request.startTime().getMinute() != 0){
            throw new GeneralException(ErrorStatus.INVALID_SCHEDULE_TIME);
        }

        // 크리에이터가 올린 영화가 맞는지 체크
        Movie movie = movieRepository.findByMovieId(request.movieId()).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(!movie.getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.MOVIE_INVALID_CREATOR);
        }

        // public 인 영화만 스케줄 등록을 할 수 있다
        if(movie.getVisibility()==Movie.Visibility.PRIVATE){
            throw new GeneralException(ErrorStatus.MOVIE_NOT_PUBLIC);
        }

        Schedule schedule = Schedule.builder()
                .startTime(request.startTime())
                .endTime(request.endTime())
                .isConfirmed(false)
                .remainingSeats(100)
                .movie(movie)
                .build();
        scheduleRepository.save(schedule);
    }

    @Override
    public List<DraftScheduleResponse> getDraftSchedule(UUID creatorId, LocalDate date) {
        List<Schedule> schedules = scheduleRepository.findAllByCreatorIdAndDate(creatorId, date);

        return schedules.stream()
                .map(schedule ->
                    new DraftScheduleResponse(schedule.getScheduleId(), schedule.getMovie().getTitle(), schedule.getStartTime(), schedule.getEndTime(), schedule.getIsConfirmed(), schedule.getStatus().name())
                ).toList();
    }

    @Override
    @Transactional
    public void confirmSchedule(UUID creatorId, List<UpdateConfirmRequest> requests) {

        /*
          1단계: request들끼리 겹치는지 검증
          DB 조회 없이 request 리스트 내에서 시간 겹침 체크. 하나라도 겹치면 즉시 예외.

          2단계: 각 scheduleId 조회 + creatorId 권한 체크
          존재하는 schedule인지 확인, creatorId가 일치하는 지 확인.

          3단계: 기존 확정된 일정과 겹치는지 검증
          existsOverlapping으로 DB 조회. 단, 현재 확정하려는 것들끼리는 제외하고 조회해야 합니다.

          4단계: 모두 통과하면 confirm 처리
        * */
        List<Schedule> schedules = new ArrayList<>(requests.stream()
                .map(request -> scheduleRepository.findById(request.scheduleId())
                        .orElseThrow(() -> new GeneralException(ErrorStatus.SCHEDULE_NOT_FOUND)))
                .toList());

        schedules.sort(Comparator.comparing(Schedule::getStartTime));

        for (int i = 0; i < schedules.size() - 1; i++) {
            if (schedules.get(i).getEndTime().isAfter(schedules.get(i + 1).getStartTime())) {
                throw new GeneralException(ErrorStatus.REQUEST_TIME_CONFLICT);
            }
        }

        schedules.forEach(
                schedule -> {
                    if(!schedule.getMovie().getCreatorId().equals(creatorId)){
                        throw new GeneralException(ErrorStatus.SCHEDULE_INVALID_CREATOR);
                    }
                    if(scheduleRepository.existsOverlapping(creatorId, schedule.getStartTime(), schedule.getEndTime())){
                        throw new GeneralException(ErrorStatus.SCHEDULE_TIME_CONFLICT);
                    }
                    schedule.confirm();
                    schedule.scheduled();
                }
        );
    }

    @Override
    @Transactional
    public void delete(UUID creatorId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(()->new GeneralException(ErrorStatus.SCHEDULE_NOT_FOUND));
        if(!schedule.getMovie().getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.SCHEDULE_INVALID_CREATOR);
        }
        if(schedule.getIsConfirmed()){
            throw new GeneralException(ErrorStatus.SCHEDULE_ALREADY_CONFIRMED);
        }
        scheduleRepository.delete(schedule);
    }

    @Override
    public List<ScheduleForUserResponse> getSpecificMovieSchedule(Long movieId) {
        log.info("[getSpecificMovieSchedule] movieId={}", movieId);
        movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        // 오늘 날짜 기준 앞으로의 일정을 나타내기 (현 시각 기준 상영 중이여도 보이게)
        // 확정된 일정만
        List<Schedule> schedules = scheduleRepository.findUpcomingByMovieId(movieId, LocalDateTime.now());
        log.info("[getSpecificMovieSchedule] 조회된 스케줄 수={}", schedules.size());

        return schedules.stream()
                .map(schedule -> new ScheduleForUserResponse(schedule.getScheduleId(), schedule.getStartTime(), schedule.getRemainingSeats(), schedule.getStatus().name()))
                .toList();
    }

    @Override
    public List<ScheduleForCreatorResponse> getByCreatorAndDate(UUID creatorId, LocalDate date) {
        // 해당 크리에이터가 만든 모든 영화의 스케줄 중에서 해당 날짜의 확정이 된 것들만 반환 (startTime이 date인?)
        List<Schedule> schedules = scheduleRepository.findAllByCreatorIdAndDate(creatorId, date);

        return schedules.stream()
                .filter(schedule -> schedule.getIsConfirmed())
                .map(schedule -> new ScheduleForCreatorResponse(schedule.getScheduleId(), schedule.getMovie().getTitle(), schedule.getStartTime(), schedule.getEndTime(), schedule.getTotalSeats(), schedule.getRemainingSeats()))
                .toList();
    }
}
