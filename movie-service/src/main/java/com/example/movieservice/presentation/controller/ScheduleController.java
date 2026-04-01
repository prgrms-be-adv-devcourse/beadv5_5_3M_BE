package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.presentation.dto.request.schedule.RegisterScheduleRequest;
import com.example.movieservice.presentation.dto.request.schedule.UpdateConfirmRequest;
import com.example.movieservice.presentation.dto.response.schedule.DraftScheduleResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForCreatorResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Schedule", description = "상영 일정 관련 API")
@RestController
@RequestMapping("/api/movies/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleUseCase scheduleUseCase;

    @Operation(summary = "상영 일정 등록 (크리에이터)", description = "크리에이터가 영화의 상영 일정을 등록합니다. 시작 시간은 정각이어야 합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "시작 시간이 정각이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음 또는 비공개 영화"),
    })
    @PostMapping("/creator/register")
    public ResponseEntity<Void> register(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody RegisterScheduleRequest request
    ){
        scheduleUseCase.register(creatorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "특정 날짜 편성 목록 조회 (크리에이터)", description = "크리에이터가 특정 날짜에 등록된 상영 일정 목록을 조회합니다. 확정 여부와 관계없이 모두 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/draft")
    public ResponseEntity<List<DraftScheduleResponse>> getDraftSchedule(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", required = true, example = "2026-04-01")
            @RequestParam LocalDate date
    ){
        return ResponseEntity.ok(scheduleUseCase.getDraftSchedule(creatorId, date));
    }

    @Operation(summary = "상영 일정 확정 (크리에이터)", description = "크리에이터가 등록된 상영 일정을 확정합니다. 요청한 일정들끼리 겹치거나 기존 확정된 일정과 겹치면 전체 실패합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "확정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스케줄에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "요청한 일정들끼리 시간이 겹침"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 확정된 다른 일정과 시간이 겹침")
    })
    @PatchMapping("/creator/confirm")
    public ResponseEntity<Void> confirmSchedule(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody List<UpdateConfirmRequest> requests
    ){
        scheduleUseCase.confirmSchedule(creatorId, requests);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "미확정 상영 일정 삭제 (크리에이터)", description = "크리에이터가 확정되지 않은 상영 일정을 삭제합니다. 이미 확정된 일정은 삭제할 수 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 확정된 스케줄은 삭제할 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스케줄에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음")
    })
    @DeleteMapping("/creator/{scheduleId}")
    public ResponseEntity<Void> deleteDraftSchedule(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "삭제할 스케줄 ID", required = true)
            @PathVariable Long scheduleId
    ){
        scheduleUseCase.delete(creatorId, scheduleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "특정 영화 상영 일정 조회 (사용자)", description = "특정 영화의 확정된 상영 일정 중 현재 시각 이후의 목록을 조회합니다. 상영 중인 일정도 포함됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @GetMapping
    public ResponseEntity<List<ScheduleForUserResponse>> getSpecificMovie(
            @Parameter(description = "조회할 영화 ID", required = true)
            @RequestParam Long movieId
    ){
        return ResponseEntity.ok(scheduleUseCase.getSpecificMovieSchedule(movieId));
    }

    @Operation(summary = "특정 날짜 확정 일정 조회 (크리에이터)", description = "크리에이터가 특정 날짜에 확정된 상영 일정 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/search")
    public ResponseEntity<List<ScheduleForCreatorResponse>> getByCreatorAndDate(
            @Parameter(description = "크리에이터 ID", required = true)
            @RequestParam UUID creatorId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", required = true, example = "2026-04-01")
            @RequestParam LocalDate date
    ){
        return ResponseEntity.ok(scheduleUseCase.getByCreatorAndDate(creatorId, date));
    }

}
