package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.MovieInternalUseCase;
import com.example.creatorservice.presentation.dto.MovieLocationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Movie Internal", description = "영화 내부 API (서비스 간 통신 전용)")
@RestController
@RequestMapping("/internal/movies")
@RequiredArgsConstructor
public class MovieInternalController {

    private final MovieInternalUseCase movieInternalUseCase;

    @Operation(summary = "영상 위치 조회", description = "streaming-service 가 세션 발급 직전에 영상 경로를 지연 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MovieLocationResponse.class))),
            @ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음", content = @Content)
    })
    @GetMapping("/{movieId}/location")
    public MovieLocationResponse getLocation(
            @Parameter(description = "영화 ID", example = "100", required = true)
            @PathVariable Long movieId) {
        return movieInternalUseCase.getLocation(movieId);
    }
}