package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.global.response.ApiResponse;
import com.example.movieservice.presentation.dto.request.RegisterScheduleRequest;
import com.example.movieservice.presentation.dto.request.UpdateConfirmRequest;
import com.example.movieservice.presentation.dto.response.DraftScheduleResponse;
import com.example.movieservice.presentation.dto.response.ScheduleForUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleUseCase scheduleUseCase;


    @Operation(summary = "상영 일정 등록 (크리에이터)", description = "크리에이터가 영화의 상영 일정을 등록합니다. 시작 시간은 정각이어야 합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "시작 시간이 정각이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "비공개 영화입니다"),
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/register")
    public ApiResponse<Void> register(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody RegisterScheduleRequest request
    ){
        scheduleUseCase.register(creatorId, request);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "특정 날짜 편성 목록 조회 (크리에이터)", description = "크리에이터가 특정 날짜에 등록된 상영 일정 목록을 조회합니다. 확정 여부와 관계없이 모두 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/draft")
    public ApiResponse<List<DraftScheduleResponse>> getDraftSchedule(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @RequestParam LocalDate date
    ){
        return ApiResponse.onSuccess(scheduleUseCase.getDraftSchedule(creatorId, date));
    }

    @Operation(summary = "상영 일정 확정 (크리에이터)", description = "크리에이터가 등록된 상영 일정을 확정합니다. 요청한 일정들끼리 겹치거나 기존 확정된 일정과 겹치면 전체 실패합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "확정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스케줄에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "일정이 겹침")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/creator/confirm")
    public ApiResponse<Void> confirmSchedule(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody List<UpdateConfirmRequest> requests
    ){
        scheduleUseCase.confirmSchedule(creatorId, requests);
        return ApiResponse.onSuccess();
    }

    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> deleteDraftSchedule(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @PathVariable Long scheduleId
    ){
        scheduleUseCase.delete(creatorId, scheduleId);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "특정 영화 상영 일정 조회 (사용자)", description = "특정 영화의 확정된 상영 일정 중 현재 시각 이후의 목록을 조회합니다. 상영 중인 일정도 포함됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @GetMapping("")
    public ApiResponse<List<ScheduleForUserResponse>> getSpecificMovie(
            @RequestParam Long movieId
    ){
        return ApiResponse.onSuccess(scheduleUseCase.getSpecificMovieSchedule(movieId));
    }
//
//    @GetMapping("")
//    public ApiResponse<> checkSpecificCreator(
//            @RequestHeader("X-Creator-Id") UUID creatorId,
//            @RequestParam LocalDate date
//    ){
//        return ApiResponse.onSuccess();
//    }

}
