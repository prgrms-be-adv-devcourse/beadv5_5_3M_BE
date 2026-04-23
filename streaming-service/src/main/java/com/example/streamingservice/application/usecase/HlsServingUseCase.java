package com.example.streamingservice.application.usecase;

import com.example.streamingservice.application.dto.HlsServeResult;
import com.example.streamingservice.application.dto.ServeHlsQuery;

public interface HlsServingUseCase {

	HlsServeResult serve(ServeHlsQuery query);
}