package com.example.gatewayservice.config;

import com.example.gatewayservice.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authorization.AuthorizationContext;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;

    private final static String[] PERMITALL_ANTPATTERNS = {
            "/", "/csrf",
            "/api/users/login", "/api/users/email/check", "/api/users/nickname/check", "/api/users/refresh",
            "/api/creators/login", "/api/creators/email/check", "/api/creators/nickname/check", "/api/creators/refresh",
            "/?*-service/actuator/?*", "/actuator/?*",
            "/actuator/gateway/**",
            "/v3/api-docs/**", "/?*-service/v3/api-docs", "/swagger*/**", "/webjars/**"
    };
    private final static String USER_SIGNUP_ANTPATTERNS = "/api/users/join";
    private final static String CREATOR_SIGNUP_ANTPATTERNS = "/api/creators/join";

    @Bean
    public SecurityWebFilterChain configure(ServerHttpSecurity http, ReactiveAuthorizationManager<AuthorizationContext> check) throws Exception {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(h -> h.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable).disable())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeExchange(auth -> auth
                        .pathMatchers(PERMITALL_ANTPATTERNS).permitAll()
                        .pathMatchers(HttpMethod.POST, USER_SIGNUP_ANTPATTERNS).permitAll()
                        .pathMatchers(HttpMethod.POST, CREATOR_SIGNUP_ANTPATTERNS).permitAll()
                        .anyExchange().access(check)
                )
                .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        return http.build();
    }

}
