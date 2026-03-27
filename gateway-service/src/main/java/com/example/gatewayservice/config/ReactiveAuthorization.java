package com.example.gatewayservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ReactiveAuthorization implements ReactiveAuthorizationManager<AuthorizationContext> {

    @Value("${apigateway.host:http://localhost:8085}")
    private String APIGATEWAY_HOST;

    public static final String AUTHORIZATION_URI = "/api/users/authorization/check";

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        RequestPath requestPath = request.getPath();
        HttpMethod httpMethod = request.getMethod();

        String baseUrl =
                APIGATEWAY_HOST + AUTHORIZATION_URI + "?httpMethod=" + httpMethod + "&requestPath=" + requestPath;
        log.info("baseUrl={}", baseUrl);

        String userId = request.getHeaders().getFirst("X-User-Id");

        boolean granted = false;
        try {
            Mono<Boolean> body = WebClient.create(baseUrl)
                    .get()
                    .header("X-User-Id", userId)
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
