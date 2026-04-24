package com.example.streamingservice.application.service;

import com.example.streamingservice.application.dto.HlsResource;
import com.example.streamingservice.application.dto.HlsServeResult;
import com.example.streamingservice.application.dto.ServeHlsQuery;
import com.example.streamingservice.application.dto.SessionMeta;
import com.example.streamingservice.application.exception.ScheduleException;
import com.example.streamingservice.application.exception.SessionException;
import com.example.streamingservice.application.port.SessionCachePort;
import com.example.streamingservice.application.port.StreamAddressPort;
import com.example.streamingservice.application.port.StreamTokenPort;
import com.example.streamingservice.application.usecase.HlsServingUseCase;
import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;
import com.example.streamingservice.infrastructure.websocket.ManifestRewriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HlsServingService implements HlsServingUseCase {

	private static final String MANIFEST_CACHE_CONTROL = "no-store";
	private static final String SEGMENT_CACHE_CONTROL = "public, max-age=3600";
	private static final String MANIFEST_SUFFIX = ".m3u8";

	private final StreamTokenPort streamToken;
	private final SessionCachePort sessionCache;
	private final ScheduleRepository scheduleRepository;
	private final StreamAddressPort streamAddress;

	private final String publicBaseUrl;

	public HlsServingService(StreamTokenPort streamToken,
	                         SessionCachePort sessionCache,
	                         ScheduleRepository scheduleRepository,
	                         StreamAddressPort streamAddress,
	                         @Value("${streaming.address.public-base-url}") String publicBaseUrl) {
		this.streamToken = streamToken;
		this.sessionCache = sessionCache;
		this.scheduleRepository = scheduleRepository;
		this.streamAddress = streamAddress;
		this.publicBaseUrl = publicBaseUrl;
	}

	@Override
	public HlsServeResult serve(ServeHlsQuery query) {
		UUID sessionId = streamToken.parse(query.token());

		SessionMeta meta = sessionCache.findBySession(sessionId)
			.orElseThrow(SessionException::sessionExpired);

		if (meta.scheduleId() != query.scheduleId()) {
			throw SessionException.sessionMismatch();
		}

		Schedule schedule = scheduleRepository.findById(query.scheduleId())
			.orElseThrow(ScheduleException::notFound);

		if (!schedule.canServeHls(Instant.now())) {
			throw SessionException.windowClosed();
		}

		HlsResource resource = streamAddress.openSegment(
			query.scheduleId(), schedule.getVideoPath(), query.file());

		if (query.file().endsWith(MANIFEST_SUFFIX)) {
			return rewriteManifest(resource, query);
		}
		return new HlsServeResult(
			resource.body(), resource.contentType(), SEGMENT_CACHE_CONTROL, resource.contentLength());
	}

	private HlsServeResult rewriteManifest(HlsResource resource, ServeHlsQuery query) {
		try (InputStream in = resource.body()) {
			byte[] raw = in.readAllBytes();
			String manifest = new String(raw, StandardCharsets.UTF_8);
			String rewritten = ManifestRewriter.rewrite(
				manifest, query.scheduleId(), query.token(), publicBaseUrl);
			byte[] out = rewritten.getBytes(StandardCharsets.UTF_8);
			return new HlsServeResult(
				new ByteArrayInputStream(out),
				resource.contentType(),
				MANIFEST_CACHE_CONTROL,
				out.length);
		} catch (IOException e) {
			throw SessionException.segmentNotFound(e);
		} catch (IllegalStateException e) {
			throw SessionException.segmentNotFound(e);
		}
	}
}