package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.global.response.ApiResponse;
import com.example.movieservice.presentation.dto.request.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.RegisterMovieResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    //    @PatchMapping("/{movieId}/detail")
//    public ApiResponse<Void> updateDetail(
//            @PathVariable Long movieId,
//            @Valid @RequestBody UpdateDetailRequest request){
////        movieUseCase.updateVisibility(movieId, request);
//        return ApiResponse.onSuccess();
//    }
}
