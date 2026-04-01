package com.example.gatewayservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class ReactiveAuthorization implements ReactiveAuthorizationManager<AuthorizationContext> {

    public static final String USER_AUTHORIZATION_URI = "/api/users/authorization/check";
    public static final String CREATOR_AUTHORIZATION_URI = "/api/creators/authorization/check";

    private final List<String> CREATOR_REQUEST_PATH = List.of("/api/settlements", "/api/wallets", "/api/movies/creator", "/api/movies/schedules/creator", "/api/movies/categories");

    @Value("${user-service.host:http://localhost:8085}")
    private String USER_SERVICE_HOST;
    @Value("${creator-service.host:http://localhost:8080}")
    private String CREATOR_SERVICE_HOST;



    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        RequestPath requestPath = request.getPath();
        HttpMethod httpMethod = request.getMethod();

        String baseUrl = USER_SERVICE_HOST + USER_AUTHORIZATION_URI + "?httpMethod=" + httpMethod + "&requestPath=" + requestPath;;
        if (CREATOR_REQUEST_PATH.stream().anyMatch(path -> requestPath.toString().startsWith(path))) {
            baseUrl = CREATOR_SERVICE_HOST + CREATOR_AUTHORIZATION_URI + "?httpMethod=" + httpMethod + "&requestPath=" + requestPath;
        }
        log.info("baseUrl={}", baseUrl);

        String userId = request.getHeaders().getFirst("X-User-Id");
        log.info("userId = {}", userId);

        if (userId == null) {
            log.warn("X-User-Id 헤더 없음 - 인증되지 않은 요청");
            return Mono.error(new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다."));
        }

        boolean granted = false;
        try {
            String key = "X-User-Id";
            if (baseUrl.startsWith(CREATOR_SERVICE_HOST)) {
                key = "X-Creator-Id";
            }
            log.info("key = {}, userId = {}", key, userId);
            Mono<Boolean> body = WebClient.create(baseUrl)
                    .get()
                    .header(key, userId)
                    .retrieve().bodyToMono(Boolean.class);
            granted = body.toFuture().get().booleanValue();
            log.info("Security AuthorizationDecision granted={}", granted);
        } catch (Exception e) {
            log.error("인가 서버에 요청 중 오류 : {}", e.getMessage());
            throw new AuthorizationServiceException("인가 요청시 오류 발생");
        }

        return Mono.just(new AuthorizationDecision(granted));
    }
}
