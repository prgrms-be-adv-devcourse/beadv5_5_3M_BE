package com.example.gatewayservice.util;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.token.public}")
    private String tokenPublic;

    public Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(loadPublicKey())
                    .build().parseSignedClaims(token).getPayload();

        } catch (ExpiredJwtException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new JwtException("Expired token");
        } catch (JwtException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new JwtException("JWT error");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private PublicKey loadPublicKey() {
        try {
            String cleaned = tokenPublic
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid public key", e);
        }
    }
}
