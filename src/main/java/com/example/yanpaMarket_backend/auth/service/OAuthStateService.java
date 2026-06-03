package com.example.yanpaMarket_backend.auth.service;

import com.example.yanpaMarket_backend.config.properties.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

import static java.security.MessageDigest.isEqual;

@Component
public class OAuthStateService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtProperties jwtProperties;
    private final java.time.Clock clock = java.time.Clock.systemUTC();

    public OAuthStateService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Issue a signed state token that expires after a short TTL.
     * This avoids in-memory loss across process restarts.
     */
    public String issueState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);

        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiresAt = Instant.now(clock).plus(TTL).getEpochSecond();
        String payload = nonce + "." + expiresAt;
        String signature = sign(payload);

        return payload + "." + signature;
    }

    /**
     * Validate the signed state token and its expiration.
     */
    public boolean validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        String[] parts = state.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        String payload = parts[0] + "." + parts[1];
        String expectedSignature = sign(payload);
        if (!isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            return false;
        }

        return expiresAt > Instant.now(clock).getEpochSecond();
    }

    private String sign(String payload) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured for OAuth state signing.");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign OAuth state.", exception);
        }
    }
}
