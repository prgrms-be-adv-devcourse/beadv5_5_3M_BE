package com.example.streamingservice.application.dto;

import java.io.InputStream;

public record HlsServeResult(
		InputStream body,
		String contentType,
		String cacheControl,
		long contentLength
) {
}