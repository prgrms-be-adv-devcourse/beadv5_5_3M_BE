package com.example.streamingservice.infrastructure.http;

import com.example.streamingservice.application.dto.MovieLocation;
import com.example.streamingservice.application.exception.ScheduleException;
import com.example.streamingservice.application.port.MovieLocationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CreatorMovieLocationAdapter implements MovieLocationPort {

	private final RestClient creatorRestClient;

	@Override
	public MovieLocation fetch(long movieId) {
		try {
			MovieLocation body = creatorRestClient.get()
				.uri("/internal/movies/{id}/location", movieId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					throw ScheduleException.streamLocationUnavailable();
				})
				.body(MovieLocation.class);
			if (body == null) {
				throw ScheduleException.streamLocationUnavailable();
			}
			return body;
		} catch (ScheduleException e) {
			throw e;
		} catch (ResourceAccessException e) {
			throw ScheduleException.streamLocationUnavailable(e);
		} catch (RuntimeException e) {
			throw ScheduleException.streamLocationUnavailable(e);
		}
	}
}