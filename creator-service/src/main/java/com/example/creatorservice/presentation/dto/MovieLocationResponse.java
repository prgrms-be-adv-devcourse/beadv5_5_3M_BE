package com.example.creatorservice.presentation.dto;

import com.example.creatorservice.domain.model.Movie;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "영화 위치 조회 응답 (내부 서비스 전용)")
public record MovieLocationResponse(
        @Schema(description = "영화 ID", example = "100")
        Long movieId,
        @Schema(description = "영상 상대 경로 (HLS: .m3u8)", example = "movies/abc-uuid/index.m3u8")
        String videoUrl,
        @Schema(description = "영상 길이 (초)", example = "7200")
        Integer runningTime,
        @Schema(description = "영화 제목")
        String title,
        @Schema(description = "크리에이터 ID")
        UUID creatorId
) {
    public static MovieLocationResponse from(Movie movie) {
        return new MovieLocationResponse(
                movie.getMovieId(),
                movie.getVideoUrl(),
                movie.getRunningTime(),
                movie.getTitle(),
                movie.getCreatorId()
        );
    }
}