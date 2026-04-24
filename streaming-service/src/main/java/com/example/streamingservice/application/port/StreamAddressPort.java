package com.example.streamingservice.application.port;

import com.example.streamingservice.application.dto.HlsResource;

public interface StreamAddressPort {

	String resolveManifestUrl(long scheduleId, String videoPath);

	HlsResource openSegment(long scheduleId, String videoPath, String fileName);
}