package com.example.gatewayservice.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GlobalGatewayFilterFactory extends AbstractGatewayFilterFactory<GlobalGatewayFilterFactory.Config> {

    public GlobalGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        // Pre filter
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            if (config.isPreLogger()) {
                log.info("[GlobalFilter Start] request ID: {}, method: {}, path: {}", request.getId(), request.getMethod(), request.getPath());
            }

            // Post Filter
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                if (config.isPostLogger()) {
                    log.info("[GlobalFilter End  ] request ID: {}, method: {}, path: {}, statusCode: {}", request.getId(), request.getMethod(), request.getPath(), response.getStatusCode());
                }
            }));
        });
    }

    @Data
    public static class Config {
        // put the configure
        private String baseMessage;
        private boolean preLogger;
        private boolean postLogger;
    }
}
