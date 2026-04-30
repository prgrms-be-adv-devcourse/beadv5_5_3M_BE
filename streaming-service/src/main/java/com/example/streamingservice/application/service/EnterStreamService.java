package com.example.streamingservice.application.service;

import com.example.streamingservice.application.dto.ActiveSession;
import com.example.streamingservice.application.dto.IssueSessionCommand;
import com.example.streamingservice.application.dto.MovieLocation;
import com.example.streamingservice.application.dto.ScheduleSummary;
import com.example.streamingservice.application.dto.SessionIssueResult;
import com.example.streamingservice.application.exception.ScheduleException;
import com.example.streamingservice.application.exception.SessionException;
import com.example.streamingservice.application.port.KickNotifierPort;
import com.example.streamingservice.application.port.MovieLocationPort;
import com.example.streamingservice.application.port.SessionCachePort;
import com.example.streamingservice.application.port.StreamAddressPort;
import com.example.streamingservice.application.port.StreamTokenPort;
import com.example.streamingservice.application.usecase.EnterStreamUseCase;
import com.example.streamingservice.domain.EntitlementRepository;
import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;
import com.example.streamingservice.domain.SessionKickReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EnterStreamService implements EnterStreamUseCase {

	private static final String WS_ENDPOINT = "/ws/stream";
	private static final Duration POST_GRACE = Duration.ofMinutes(3);

	private final ScheduleRepository scheduleRepository;
	private final EntitlementRepository entitlementRepository;
	private final SessionCachePort sessionCache;
	private final StreamTokenPort streamToken;
	private final MovieLocationPort movieLocation;
	private final StreamAddressPort streamAddress;
	private final KickNotifierPort kickNotifier;

	@Override
	public SessionIssueResult issue(IssueSessionCommand command) {
		Schedule schedule = scheduleRepository.findById(command.scheduleId())
			.orElseThrow(ScheduleException::notFound);

		Instant now = Instant.now();
		if (!schedule.canEnterSession(now)) {
			throw SessionException.windowClosed();
		}

		entitlementRepository.find(command.userId(), command.scheduleId())
			.orElseThrow(SessionException::noEntitlement);

		sessionCache.findByUser(command.userId()).ifPresent(existing -> {
			kickNotifier.notify(command.userId(), SessionKickReason.DUPLICATE_LOGIN);
			sessionCache.evict(command.userId(), existing.sessionId());
		});

		if (schedule.getVideoPath() == null) {
			MovieLocation location = movieLocation.fetch(schedule.getMovieId());
			schedule.attachVideoLocation(location.videoUrl(), location.runningTime());
		}

		UUID sessionId = UUID.randomUUID();
		Instant expiresAt = schedule.getEndTime().plus(POST_GRACE);
		String token = streamToken.issue(sessionId, expiresAt);

		Duration ttl = Duration.between(now, expiresAt);
		sessionCache.put(
			command.userId(),
			new ActiveSession(sessionId, schedule.getScheduleId(), now),
			ttl
		);

		String manifestBase = streamAddress.resolveManifestUrl(schedule.getScheduleId(), schedule.getVideoPath());
		String manifestUrl = manifestBase + "?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

		ScheduleSummary summary = new ScheduleSummary(
			schedule.getScheduleId(),
			schedule.getTitle(),
			schedule.getStartTime(),
			schedule.getEndTime(),
			schedule.getImageUrl()
		);

		return new SessionIssueResult(token, sessionId, manifestUrl, WS_ENDPOINT, expiresAt, summary);
	}
}