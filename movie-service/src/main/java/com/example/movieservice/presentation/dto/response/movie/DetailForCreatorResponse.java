package com.example.movieservice.presentation.dto.response.movie;

import java.util.List;

public record DetailForCreatorResponse(
        String title,
        String description,
        List<Long> categoryIds,
        Integer baseCookie,
        Integer additionalCookie
        // 여기에 이미지 추가
) {
}
