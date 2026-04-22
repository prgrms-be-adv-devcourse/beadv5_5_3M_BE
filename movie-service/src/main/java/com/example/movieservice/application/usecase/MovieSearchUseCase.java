package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.response.movie.AutocompleteResponse;
import com.example.movieservice.presentation.dto.response.movie.CategoryFilterCountResponse;
import com.example.movieservice.presentation.dto.response.movie.MovieSearchResponse;
import com.example.movieservice.presentation.dto.response.movie.PopularKeywordResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MovieSearchUseCase {

    // ── 기본 검색 ──
    MovieSearchResponse searchMovies(String keyword, List<Long> categoryIds, Pageable pageable);

    // ── 색인 관리 ──
    void indexMovie(Long movieId);

    void indexMovieFromMessage(Long movieId, String title, String description,
                               String creatorId, String creatorNickname,
                               List<Long> categoryIds, List<String> categoryNames);

    void updateMovieIndex(Long movieId, String title, String description,
                          List<Long> categoryIds, List<String> categoryNames);

    void updateMovieVisibility(Long movieId, String visibility);

    void deleteMovieIndex(Long movieId);

    // ── 확장 기능 1: 자동완성 ──
    AutocompleteResponse autocomplete(String prefix, int size);

    // ── 확장 기능 2: 인기 검색어 ──
    void recordSearchKeyword(String keyword);

    PopularKeywordResponse getPopularKeywords(int size);

    // ── 확장 기능 3: 필터 카운트 ──
    CategoryFilterCountResponse getFilterCounts();
}
