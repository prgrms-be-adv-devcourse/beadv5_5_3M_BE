package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.global.response.ApiResponse;
import com.example.movieservice.presentation.dto.request.movie.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateDetailRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.movie.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Movie", description = "영화 관련 API")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieUseCase movieUseCase;

    @Operation(summary = "영화 등록", description = "크리에이터가 새로운 영화를 등록합니다. 최대 3개까지 등록 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공, 생성된 movieId 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "영화 등록 한도 초과 또는 유효성 검사 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음")
    })
    @PostMapping("/register")
    public ApiResponse<RegisterMovieResponse> register(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody RegisterMovieRequest request){
        // 용량 체크는 프론트에서 메타데이터로 진행
        // todo : 포스터 이미지와 영상 업로드는 진행해야 함
        RegisterMovieResponse response = movieUseCase.register(creatorId, request);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "영화 공개 여부 변경", description = "영화의 공개(PUBLIC) / 비공개(PRIVATE) 상태를 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{movieId}/visibility")
    public ApiResponse<Void> updateVisibility(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "변경할 영화 ID", required = true)
            @PathVariable Long movieId,
            @Valid @RequestBody UpdateVisibilityRequest request){
        movieUseCase.updateVisibility(creatorId, movieId, request);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "영화 상세 정보 수정", description = "크리에이터가 본인 영화의 제목, 설명, 카테고리, 추가 쿠키를 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화 또는 카테고리를 찾을 수 없음")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{movieId}/detail")
    public ApiResponse<Void> updateDetail(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @PathVariable Long movieId,
            @Valid @RequestBody UpdateDetailRequest request){
        // todo : 포스터 이미지 수정도 로직에 추가해야 함
        movieUseCase.updateDetail(creatorId, movieId, request);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "영화 삭제", description = "크리에이터가 본인 영화를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{movieId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @PathVariable Long movieId){
        // todo : 나중에 s3에 올라간 자원을 지우는 로직이 들어가야 함
        movieUseCase.delete(creatorId, movieId);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "영화 상세 조회 (크리에이터)", description = "크리에이터가 본인 영화의 수정용 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @GetMapping("/{movieId}/detail/creator")
    public ApiResponse<DetailForCreatorResponse> detailForCreator(
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @PathVariable Long movieId
    ){
        // todo: 이미지도 반환해야 함
        return ApiResponse.onSuccess(movieUseCase.getDetailForCreator(creatorId, movieId));
    }

    @Operation(summary = "영화 상세 조회 (사용자)", description = "사용자가 공개된 영화의 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음 또는 비공개 영화")
    })
    @GetMapping("/{movieId}/detail")
    public ApiResponse<DetailForUserResponse> detailForUser(
            @PathVariable Long movieId
    ){
        // todo: 이미지 반환해야 함
        return ApiResponse.onSuccess(movieUseCase.getDetailForUser(movieId));
    }

    @Operation(summary = "크리에이터 영화 목록 조회 (사용자)", description = "특정 크리에이터의 공개된 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/list")
    public ApiResponse<List<MovieByCreatorResponse>> getMovieListByCreator(
            @RequestParam("creatorId") UUID creatorId
    ){
        // todo: 나중에 이미지도 추가
        return ApiResponse.onSuccess(movieUseCase.getMovieListByCreator(creatorId));
    }

    @Operation(summary = "내 영화 목록 조회 (크리에이터)", description = "크리에이터가 본인의 전체 영화 목록을 공개 여부와 함께 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/list")
    public ApiResponse<List<MovieForCreatorResponse>> getMovieListForCreator(
            @RequestHeader("X-Creator-Id") UUID creatorId
    ){
        // todo: 나중에 이미지도 추가
        return ApiResponse.onSuccess(movieUseCase.getMovieListForCreator(creatorId));
    }

    @Operation(summary = "편성 가능한 영화 목록 조회 (크리에이터)", description = "크리에이터가 일정 편성 시 선택할 수 있는 공개된 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/schedulable")
    public ApiResponse<List<MovieForScheduleResponse>> getPublicMovieListForSchedule(
            @RequestHeader("X-Creator-Id") UUID creatorId
    ){
        return ApiResponse.onSuccess(movieUseCase.getPublicMovieListForSchedule(creatorId));
    }

    @Operation(summary = "현재 상영 중인 영화 목록 조회", description = "현재 ON_AIR 상태인 스케줄의 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/on-air")
    public ApiResponse<List<MovieCardResponse>> getOnAirMovieList(){
        return ApiResponse.onSuccess(movieUseCase.getOnAirMovieList());
    }

    @Operation(summary = "상영 예정 영화 목록 조회", description = "SCHEDULED 상태인 스케줄 중 영화별 가장 빠른 상영 일정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/scheduled")
    public ApiResponse<List<ScheduledMovieResponse>> getScheduledMovieList(){
        // todo : 나중에 page를 추가해서 특정 개수만 반환하는 등의 작업 필요
        return ApiResponse.onSuccess(movieUseCase.getScheduledMovieList());
    }

    @Operation(summary = "전체 공개 영화 목록 조회", description = "공개(PUBLIC) 상태인 전체 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/public")
    public ApiResponse<List<MovieCardResponse>> getPublicMovieList(){
        return ApiResponse.onSuccess(movieUseCase.getPublicMovieList());
    }

    @Operation(summary = "장르별 영화 목록 조회", description = "특정 카테고리(장르)에 속한 공개 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음")
    })
    @GetMapping("/genre/{categoryId}")
    public ApiResponse<List<MovieCardResponse>> getMovieListByGenre(
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable Long categoryId) {
        return ApiResponse.onSuccess(movieUseCase.getMovieListByGenre(categoryId));
    }

}
