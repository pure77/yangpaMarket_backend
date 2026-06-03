package com.example.yanpaMarket_backend.security;

import com.example.yanpaMarket_backend.config.properties.JwtProperties;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters.");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 보호 API 인증용 access token 발급.
     */
    public String createAccessToken(String publicId, boolean isAdmin) {
        return createToken(publicId, TokenType.ACCESS, jwtProperties.getAccessExpirationSeconds(), Map.of("isAdmin", isAdmin));
    }

    /**
     * 로그인 없이 세션 유지에 사용되는 refresh token 발급.
     */
    public String createRefreshToken(String publicId) {
        return createToken(publicId, TokenType.REFRESH, jwtProperties.getRefreshExpirationSeconds(), Map.of());
    }

    /**
     * 추가 정보 입력 단계 전용 signup token 발급.
     */
    public String createSignupToken(String publicId) {
        return createToken(publicId, TokenType.SIGNUP, jwtProperties.getSignupExpirationSeconds(), Map.of());
    }

    public String createKakaoSignupToken(KakaoSignupTokenPayload payload) {
        if (payload == null || payload.providerUserId() == null || payload.providerUserId().isBlank()) {
            throw new IllegalArgumentException("Kakao providerUserId is required for signup token.");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("provider", "KAKAO");
        claims.put("providerUserId", payload.providerUserId());
        putIfPresent(claims, "email", payload.email());
        putIfPresent(claims, "nickname", payload.nickname());
        putIfPresent(claims, "profileImageUrl", payload.profileImageUrl());

        return createToken(
                payload.providerUserId(),
                TokenType.SIGNUP,
                jwtProperties.getSignupExpirationSeconds(),
                claims
        );
    }

    public KakaoSignupTokenPayload getKakaoSignupTokenPayload(String token) {
        Claims claims = parseClaims(token, TokenType.SIGNUP);
        String providerUserId = claims.get("providerUserId", String.class);
        if (providerUserId == null || providerUserId.isBlank()) {
            providerUserId = claims.getSubject();
        }

        return new KakaoSignupTokenPayload(
                providerUserId,
                claims.get("email", String.class),
                claims.get("nickname", String.class),
                claims.get("profileImageUrl", String.class)
        );
    }

    /**
     * 토큰 타입(expectedType)까지 검증 후 사용자 publicId를 추출.
     */
    public String getPublicId(String token, TokenType expectedType) {
        Claims claims = parseClaims(token, expectedType);
        return claims.getSubject();
    }

    /**
     * access token의 관리자 권한 클레임 조회.
     */
    public boolean isAdmin(String accessToken) {
        Claims claims = parseClaims(accessToken, TokenType.ACCESS);
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        return Boolean.TRUE.equals(isAdmin);
    }

    public long getAccessExpirationSeconds() {
        return jwtProperties.getAccessExpirationSeconds();
    }

    public Instant getRefreshExpiryInstant() {
        return Instant.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds());
    }

    /**
     * 공통 JWT 생성 함수.
     * tokenType 클레임을 넣어 access/refresh/signup을 명확히 구분합니다.
     */
    private String createToken(String publicId, TokenType tokenType, long expirationSeconds, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(publicId)
                .claims(extraClaims)
                .claim("tokenType", tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * JWT 서명/만료/토큰 타입을 검증합니다.
     */
    private Claims parseClaims(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String tokenType = claims.get("tokenType", String.class);
            if (!expectedType.name().equals(tokenType)) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "토큰 타입이 올바르지 않습니다.");
            }
            return claims;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }

    private void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        claims.put(key, value);
    }
}
