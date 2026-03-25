package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieUseCase movieUseCase;

    @PostMapping("")
    public ApiResponse<> register(@RequestHeader){

        return ApiResponse.onSuccess();
    }
}
