package com.example.streamingservice.application.port;

import com.example.streamingservice.application.dto.MovieLocation;

public interface MovieLocationPort {

	MovieLocation fetch(long movieId);
}