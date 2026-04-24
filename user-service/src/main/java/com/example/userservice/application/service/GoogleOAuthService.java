package com.example.userservice.application.service;

import com.example.userservice.application.dto.GoogleUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class GoogleOAuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GoogleOAuthService(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret,
            @Value("${spring.security.oauth2.client.registration.google.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public GoogleUserInfo exchangeCodeForUserInfo(String code) {
        log.info("OAuth token exchange 시작 - redirect_uri: {}, client_id: {}, code 앞 10자: {}",
                redirectUri, clientId, code.length() > 10 ? code.substring(0, 10) + "..." : code);

        // Exchange authorization code for tokens
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token",
                    request,
                    Map.class
            );
        } catch (Exception e) {
            log.error("Google token exchange 실패 - redirect_uri: {}, error: {}", redirectUri, e.getMessage());
            throw e;
        }

        String idToken = (String) response.getBody().get("id_token");
        log.info("Google OAuth token exchange successful");

        // Decode JWT payload (Base64 decode the middle part)
        String[] parts = idToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String email = jsonNode.get("email").asText();
            String name = jsonNode.get("name").asText();
            log.info("Google OAuth user info extracted: email={}", email);
            return new GoogleUserInfo(email, name);
        } catch (Exception e) {
            log.error("Failed to parse Google id_token payload", e);
            throw new RuntimeException("Failed to parse Google id_token payload", e);
        }
    }
}
