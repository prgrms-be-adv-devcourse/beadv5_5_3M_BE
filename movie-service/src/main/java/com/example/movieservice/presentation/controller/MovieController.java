package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieUseCase;
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
import org.springframework.http.ResponseEntity;
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공, 생성된 movieId 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "영화 등록 한도 초과 또는 유효성 검사 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음")
    })
    @PostMapping("/creator/register")
    public ResponseEntity<RegisterMovieResponse> register(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Valid @RequestBody RegisterMovieRequest request){
        // 용량 체크는 프론트에서 메타데이터로 진행
        // todo : 포스터 이미지와 영상 업로드는 진행해야 함
        return ResponseEntity.status(HttpStatus.CREATED).body(movieUseCase.register(creatorId, request));
    }

    @Operation(summary = "영화 공개 여부 변경", description = "영화의 공개(PUBLIC) / 비공개(PRIVATE) 상태를 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @PatchMapping("/creator/{movieId}/visibility")
    public ResponseEntity<Void> updateVisibility(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "변경할 영화 ID", required = true)
            @PathVariable Long movieId,
            @Valid @RequestBody UpdateVisibilityRequest request){
        movieUseCase.updateVisibility(creatorId, movieId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영화 상세 정보 수정", description = "크리에이터가 본인 영화의 제목, 설명, 카테고리, 추가 쿠키를 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 이미 확정된 스케줄이 존재하여 수정 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화 또는 카테고리를 찾을 수 없음")
    })
    @PatchMapping("/creator/{movieId}/detail")
    public ResponseEntity<Void> updateDetail(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "수정할 영화 ID", required = true)
            @PathVariable Long movieId,
            @Valid @RequestBody UpdateDetailRequest request){
        // todo : 포스터 이미지 수정도 로직에 추가해야 함
        movieUseCase.updateDetail(creatorId, movieId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영화 삭제", description = "크리에이터가 본인 영화를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 확정된 스케줄이 존재하여 삭제 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @DeleteMapping("/creator/{movieId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "삭제할 영화 ID", required = true)
            @PathVariable Long movieId){
        // todo : 나중에 s3에 올라간 자원을 지우는 로직이 들어가야 함
        movieUseCase.delete(creatorId, movieId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영화 상세 조회 (크리에이터)", description = "크리에이터가 본인 영화의 수정용 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 영화에 대한 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음")
    })
    @GetMapping("/creator/{movieId}/detail")
    public ResponseEntity<DetailForCreatorResponse> detailForCreator(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId,
            @Parameter(description = "조회할 영화 ID", required = true)
            @PathVariable Long movieId
    ){
        // todo: 이미지도 반환해야 함
        return ResponseEntity.ok(movieUseCase.getDetailForCreator(creatorId, movieId));
    }

    @Operation(summary = "영화 상세 조회 (사용자)", description = "사용자가 공개된 영화의 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음 또는 비공개 영화")
    })
    @GetMapping("/{movieId}/detail")
    public ResponseEntity<DetailForUserResponse> detailForUser(
            @Parameter(description = "조회할 영화 ID", required = true)
            @PathVariable Long movieId
    ){
        // todo: 이미지 반환해야 함
        return ResponseEntity.ok(movieUseCase.getDetailForUser(movieId));
    }

    @Operation(summary = "크리에이터 영화 목록 조회 (사용자)", description = "특정 크리에이터의 공개된 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/list")
    public ResponseEntity<List<MovieByCreatorResponse>> getMovieListByCreator(
            @Parameter(description = "조회할 크리에이터 ID", required = true)
            @RequestParam("creatorId") UUID creatorId
    ){
        // todo: 나중에 이미지도 추가
        return ResponseEntity.ok(movieUseCase.getMovieListByCreator(creatorId));
    }

    @Operation(summary = "내 영화 목록 조회 (크리에이터)", description = "크리에이터가 본인의 전체 영화 목록을 공개 여부와 함께 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/list")
    public ResponseEntity<List<MovieForCreatorResponse>> getMovieListForCreator(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId
    ){
        // todo: 나중에 이미지도 추가
        return ResponseEntity.ok(movieUseCase.getMovieListForCreator(creatorId));
    }

    @Operation(summary = "편성 가능한 영화 목록 조회 (크리에이터)", description = "크리에이터가 일정 편성 시 선택할 수 있는 공개된 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/creator/schedulable")
    public ResponseEntity<List<MovieForScheduleResponse>> getPublicMovieListForSchedule(
            @Parameter(description = "크리에이터 ID (Gateway에서 전달)", required = true)
            @RequestHeader("X-Creator-Id") UUID creatorId
    ){
        return ResponseEntity.ok(movieUseCase.getPublicMovieListForSchedule(creatorId));
    }

    @Operation(summary = "현재 상영 중인 영화 목록 조회", description = "현재 ON_AIR 상태인 스케줄의 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/on-air")
    public ResponseEntity<List<MovieCardResponse>> getOnAirMovieList(){
        return ResponseEntity.ok(movieUseCase.getOnAirMovieList());
    }

    @Operation(summary = "상영 예정 영화 목록 조회", description = "SCHEDULED 상태인 스케줄 중 영화별 가장 빠른 상영 일정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/scheduled")
    public ResponseEntity<List<ScheduledMovieResponse>> getScheduledMovieList(){
        // todo : 나중에 page를 추가해서 특정 개수만 반환하는 등의 작업 필요
        return ResponseEntity.ok(movieUseCase.getScheduledMovieList());
    }

    @Operation(summary = "전체 공개 영화 목록 조회", description = "공개(PUBLIC) 상태인 전체 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/public")
    public ResponseEntity<List<MovieCardResponse>> getPublicMovieList(){
        return ResponseEntity.ok(movieUseCase.getPublicMovieList());
    }

    @Operation(summary = "장르별 영화 목록 조회", description = "특정 카테고리(장르)에 속한 공개 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음")
    })
    @GetMapping("/genre/{categoryId}")
    public ResponseEntity<List<MovieCardResponse>> getMovieListByGenre(
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(movieUseCase.getMovieListByGenre(categoryId));
    }

    @Operation(summary = "영화 제목 검색", description = "제목에 검색어가 포함된 공개 영화 목록을 조회합니다. 검색어가 없으면 전체 공개 영화를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/search")
    public ResponseEntity<List<MovieCardResponse>> searchMoviesByTitle(
            @Parameter(description = "검색할 제목 키워드 (미입력 시 전체 반환)")
            @RequestParam(required = false, defaultValue = "") String title) {
        return ResponseEntity.ok(movieUseCase.searchMoviesByTitle(title));
    }

}
