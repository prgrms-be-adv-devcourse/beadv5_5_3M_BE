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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;

    private final static String[] PERMITALL_ANTPATTERNS = {
            "/", "/csrf",
            "/api/users/login", "/api/users/email/check", "/api/users/nickname/check", "/api/users/refresh",
            "/api/users/email/verification/send", "/api/users/email/verification/verify",
            "/api/creators/login", "/api/creators/email/check", "/api/creators/nickname/check", "/api/creators/refresh",
            "/api/users/oauth2/google",
            "/?*-service/actuator/?*", "/actuator/?*",
            "/actuator/gateway/**",
            "/v3/api-docs/**", "/?*-service/v3/api-docs", "/swagger*/**", "/webjars/**"
    };
    private final static String USER_SIGNUP_ANTPATTERNS = "/api/users/join";
    private final static String CREATOR_SIGNUP_ANTPATTERNS = "/api/creators/join";

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://3m-cinestream.vercel.app", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityWebFilterChain configure(ServerHttpSecurity http, ReactiveAuthorizationManager<AuthorizationContext> check) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(h -> h.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable).disable())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "**").permitAll()
                        .pathMatchers(PERMITALL_ANTPATTERNS).permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/movies/categories").permitAll()
                        .pathMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/streaming/*/*.m3u8", "/api/streaming/*/*.ts").permitAll()
                        .pathMatchers(HttpMethod.POST, USER_SIGNUP_ANTPATTERNS).permitAll()
                        .pathMatchers(HttpMethod.POST, CREATOR_SIGNUP_ANTPATTERNS).permitAll()
                        .anyExchange().access(check)
                )
                .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        return http.build();
    }

}
