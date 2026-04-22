package com.example.streamingservice.application.dto;

import java.io.InputStream;

public record HlsResource(InputStream body, String contentType, long contentLength) {
}