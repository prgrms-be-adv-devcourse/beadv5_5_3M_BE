package com.example.streamingservice.infrastructure.token;

import com.example.streamingservice.application.exception.StreamTokenException;
import com.example.streamingservice.application.port.StreamTokenPort;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JjwtStreamTokenAdapter implements StreamTokenPort {

	private final String secret;
	private final String issuer;
	private SecretKey key;

	public JjwtStreamTokenAdapter(
		@Value("${streaming.jwt.secret}") String secret,
		@Value("${streaming.jwt.issuer}") String issuer
	) {
		this.secret = secret;
		this.issuer = issuer;
	}

	@PostConstruct
	void initKey() {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String issue(UUID sessionId, Instant expiresAt) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(sessionId.toString())
			.issuer(issuer)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(key, Jwts.SIG.HS256)
			.compact();
	}

	@Override
	public UUID parse(String token) {
		try {
			String subject = Jwts.parser()
				.verifyWith(key)
				.requireIssuer(issuer)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
			return UUID.fromString(subject);
		} catch (ExpiredJwtException e) {
			throw StreamTokenException.expired(e);
		} catch (JwtException | IllegalArgumentException e) {
			throw StreamTokenException.invalid(e);
		}
	}
}