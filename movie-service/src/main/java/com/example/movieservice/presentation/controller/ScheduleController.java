package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}