package com.example.ticketservice.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "페이지네이션 응답")
public record PageResult<T>(
        @Schema(description = "조회 결과 목록") List<T> content,
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 데이터 수", example = "100") long totalElements,
        @Schema(description = "전체 페이지 수", example = "5") int totalPages
) {
    public static <T> PageResult<T> from(Page<T> springPage) {
        return new PageResult<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }
}