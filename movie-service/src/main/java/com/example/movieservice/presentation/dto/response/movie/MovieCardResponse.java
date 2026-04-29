package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "영화 카드 응답")
public record MovieCardResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID creatorId,

        @Schema(description = "크리에이터 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "포스터 이미지 URL", example = "https://example.com/image.jpg")
        String imageUrl,

        @Schema(description = "평균 평점", example = "4.5")
        Float averageRating,

        @Schema(description = "좋아요 수", example = "12")
        Integer likeCount,

        @Schema(description = "리뷰 수", example = "3")
        Integer reviewCount,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds,

        @Schema(description = "하이라이트 처리된 제목 (ES 검색 시만, <em> 태그 포함, 매칭 없으면 null)", example = "<em>인터</em>스텔라")
        String highlightedTitle,

        @Schema(description = "하이라이트 처리된 크리에이터 닉네임 (ES 검색 시만)", example = "<em>봉준</em>호감독")
        String highlightedNickname
) {
}
