package com.example.streamingservice.presentation;

import com.example.streamingservice.application.dto.HlsServeResult;
import com.example.streamingservice.application.dto.ServeHlsQuery;
import com.example.streamingservice.application.usecase.HlsServingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@RestController
@RequestMapping("/api/streaming")
@RequiredArgsConstructor
public class HlsController {

	private final HlsServingUseCase hlsServing;

	@GetMapping("/{scheduleId}/{file}")
	public ResponseEntity<StreamingResponseBody> serve(
			@PathVariable long scheduleId,
			@PathVariable String file,
			@RequestParam("t") String token) {
		HlsServeResult result = hlsServing.serve(new ServeHlsQuery(scheduleId, file, token));

		StreamingResponseBody body = out -> {
			try (InputStream in = result.body()) {
				in.transferTo(out);
			}
		};

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(result.contentType()));
		headers.setCacheControl(result.cacheControl());
		headers.setContentLength(result.contentLength());
		return ResponseEntity.ok().headers(headers).body(body);
	}
}