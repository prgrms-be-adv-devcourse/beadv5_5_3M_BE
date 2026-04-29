package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleByDateResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Schedule", description = "상영 일정 관련 API")
@RestController
@RequestMapping("/api/movies/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleUseCase scheduleUseCase;

    @Operation(summary = "특정 영화 상영 일정 조회", description = "특정 영화의 확정된 상영 일정 중 현재 시각 이후의 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @GetMapping
    public ResponseEntity<List<ScheduleForUserResponse>> getSpecificMovie(
            @Parameter(description = "조회할 영화 ID", required = true)
            @RequestParam Long movieId) {
        return ResponseEntity.ok(scheduleUseCase.getSpecificMovieSchedule(movieId));
    }

    @Operation(summary = "날짜별 스케줄 조회", description = "특정 날짜에 확정된 모든 영화의 스케줄 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/by-date")
    public ResponseEntity<List<ScheduleByDateResponse>> getSchedulesByDate(
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleUseCase.getSchedulesByDate(date));
    }
}