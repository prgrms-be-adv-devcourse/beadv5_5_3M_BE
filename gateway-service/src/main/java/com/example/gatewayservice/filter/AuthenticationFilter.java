package com.example.gatewayservice.filter;

import com.example.gatewayservice.exception.JwtAuthenticationException;
import com.example.gatewayservice.util.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements WebFilter {

    private final JwtProvider jwtProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtProvider.getClaims(token);
            String userId = claims.getSubject();

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(h -> h.remove("X-User-Id"))
                    .headers(h -> h.remove("X-Creator-Id"))
                    .header("X-User-Id", userId)
                    .header("X-Creator-Id", userId)
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());

            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            String message = "Expired token".equals(e.getMessage()) ? "TOKEN_EXPIRED" : "TOKEN_INVALID";
            return Mono.error(new JwtAuthenticationException(HttpStatus.UNAUTHORIZED, message));
        }
    }
}
