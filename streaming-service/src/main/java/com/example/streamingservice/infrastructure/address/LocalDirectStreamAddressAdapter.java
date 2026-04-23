package com.example.streamingservice.infrastructure.address;

import com.example.streamingservice.application.dto.HlsResource;
import com.example.streamingservice.application.exception.SessionException;
import com.example.streamingservice.application.port.StreamAddressPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "streaming.address.adapter", havingValue = "local-direct", matchIfMissing = true)
public class LocalDirectStreamAddressAdapter implements StreamAddressPort {

	private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]+\\.(m3u8|ts)$");
	private static final String APPLICATION_VND_APPLE_MPEGURL = "application/vnd.apple.mpegurl";
	private static final String VIDEO_MP2T = "video/mp2t";

	private final String publicBaseUrl;
	private final String storageBase;

	public LocalDirectStreamAddressAdapter(
		@Value("${streaming.address.public-base-url}") String publicBaseUrl,
		@Value("${storage.s3-path}") String storageBase
	) {
		this.publicBaseUrl = publicBaseUrl;
		this.storageBase = storageBase;
	}

	@Override
	public String resolveManifestUrl(long scheduleId, String videoPath) {
		String manifestFile = extractFileName(videoPath);
		return publicBaseUrl + "/api/streaming/" + scheduleId + "/" + manifestFile;
	}

	@Override
	public HlsResource openSegment(long scheduleId, String videoPath, String fileName) {
		if (fileName == null || !ALLOWED_FILE_NAME.matcher(fileName).matches()) {
			throw SessionException.invalidFileName();
		}

		// videoPath 는 creator-service 가 'movies/<uuid>/index.m3u8' 형태로 이미 prefix 포함
		// → baseDir 에 'movies' 를 한 번 더 붙이면 경로 중복(movies/movies/...) → 404
		Path baseDir;
		try {
			baseDir = Paths.get(storageBase).toRealPath();
		} catch (IOException e) {
			throw SessionException.segmentNotFound(e);
		}

		Path directory = baseDir.resolve(dirName(videoPath)).normalize();
		if (!directory.startsWith(baseDir)) {
			throw SessionException.invalidFileName();
		}

		Path target = directory.resolve(fileName).normalize();
		if (!target.startsWith(baseDir)) {
			throw SessionException.invalidFileName();
		}

		Path real;
		try {
			real = target.toRealPath();
		} catch (IOException e) {
			throw SessionException.segmentNotFound(e);
		}

		if (!real.startsWith(baseDir)) {
			throw SessionException.invalidFileName();
		}
		if (!Files.isRegularFile(real)) {
			throw SessionException.segmentNotFound();
		}

		try {
			InputStream body = Files.newInputStream(real);
			long size = Files.size(real);
			return new HlsResource(body, contentTypeFor(fileName), size);
		} catch (IOException e) {
			throw SessionException.segmentNotFound(e);
		}
	}

	private static String extractFileName(String videoPath) {
		if (videoPath == null || videoPath.isBlank()) {
			return "index.m3u8";
		}
		int slash = videoPath.lastIndexOf('/');
		return slash < 0 ? videoPath : videoPath.substring(slash + 1);
	}

	private static String dirName(String videoPath) {
		if (videoPath == null || videoPath.isBlank()) {
			return "";
		}
		int slash = videoPath.lastIndexOf('/');
		return slash < 0 ? "" : videoPath.substring(0, slash);
	}

	private static String contentTypeFor(String fileName) {
		if (fileName.endsWith(".m3u8")) {
			return APPLICATION_VND_APPLE_MPEGURL;
		}
		return VIDEO_MP2T;
	}
}