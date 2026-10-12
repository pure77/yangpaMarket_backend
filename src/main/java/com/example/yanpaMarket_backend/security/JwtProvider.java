package com.example.yanpaMarket_backend.security; // security = 인증/토큰 관련 클래스 모음

import com.example.yanpaMarket_backend.config.properties.JwtProperties; // 시크릿/만료시간 등 JWT 설정값
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import io.jsonwebtoken.Claims;            // JWT 본문(클레임) 표현
import io.jsonwebtoken.Jwts;              // JWT 생성/파싱 진입점
import io.jsonwebtoken.security.Keys;     // 시크릿 문자열 → 서명키 변환
import java.nio.charset.StandardCharsets;
import java.time.Instant;                 // 시각(발급/만료 계산)
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;                  // jti(토큰 고유 ID) 생성용
import javax.crypto.SecretKey;            // HMAC 서명용 비밀키
import org.springframework.stereotype.Component; // 스프링 빈 등록

/**
 * [무엇] JWT를 "발급"하고 "검증/해석"하는 핵심 컴포넌트.
 * [토큰 종류] tokenType 클레임으로 구분 (TokenType enum 참고)
 *   - ACCESS : 보호 API 인증용 단기 토큰
 *   - REFRESH: 세션 유지용 장기 토큰 (DB에 해시 저장)
 *   - SIGNUP : 회원가입 2단계 임시 토큰 (카카오 사용자 정보 포함)
 * [어떻게 쓰임]
 *   - AuthService 가 로그인/회원가입/토큰갱신 시 토큰을 발급할 때 사용.
 *   - JwtAuthenticationFilter 가 요청 토큰을 검증할 때 사용.
 * [핵심] 모든 토큰을 같은 HMAC-SHA256 시크릿으로 서명하되,
 *        tokenType 클레임으로 용도를 강제 구분 → 다른 용도로 쓰면 UNAUTHORIZED.
 */
@Component
public class JwtProvider {

    private final JwtProperties jwtProperties; // 시크릿/만료시간 설정값
    private final SecretKey secretKey;         // 서명에 사용할 비밀키(시크릿으로 1회 생성)

    /**
     * 생성자. 시크릿 강도를 검증하고 서명키를 만든다.
     */
    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // HMAC-SHA256은 최소 256비트(=32자) 키가 필요 → 약하면 아예 기동 거부(보안)
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters.");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)); // 시크릿 → 서명키
    }

    /**
     * [발급] 보호 API 인증용 ACCESS 토큰. isAdmin(관리자 여부)을 클레임에 담는다.
     */
    public String createAccessToken(String publicId, boolean isAdmin) {
        return createToken(publicId, TokenType.ACCESS, jwtProperties.getAccessExpirationSeconds(), Map.of("isAdmin", isAdmin));
    }

    /**
     * [발급] 세션 유지용 REFRESH 토큰. 추가 클레임 없음.
     */
    public String createRefreshToken(String publicId) {
        return createToken(publicId, TokenType.REFRESH, jwtProperties.getRefreshExpirationSeconds(), Map.of());
    }

    /**
     * [발급] 회원가입 단계 전용 SIGNUP 토큰(추가 정보 없는 단순형).
     */
    public String createSignupToken(String publicId) {
        return createToken(publicId, TokenType.SIGNUP, jwtProperties.getSignupExpirationSeconds(), Map.of());
    }

    /**
     * [발급] 카카오 사용자 정보를 담은 회원가입용 SIGNUP 토큰.
     * subject = 카카오 사용자 ID, 추가 클레임 = 이메일/닉네임/프로필(있을 때만).
     */
    public String createKakaoSignupToken(KakaoSignupTokenPayload payload) {
        // 카카오 사용자 ID는 필수 — 없으면 토큰을 만들 수 없음
        if (payload == null || payload.providerUserId() == null || payload.providerUserId().isBlank()) {
            throw new IllegalArgumentException("Kakao providerUserId is required for signup token.");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("provider", "KAKAO");                              // 소셜 제공자 표시
        claims.put("providerUserId", payload.providerUserId());       // 카카오 사용자 ID
        putIfPresent(claims, "email", payload.email());              // null/빈값이면 클레임에 넣지 않음
        putIfPresent(claims, "nickname", payload.nickname());
        putIfPresent(claims, "profileImageUrl", payload.profileImageUrl());

        return createToken(
                payload.providerUserId(),                    // subject로 카카오 ID 사용
                TokenType.SIGNUP,
                jwtProperties.getSignupExpirationSeconds(),
                claims
        );
    }

    /**
     * [해석] SIGNUP 토큰에서 카카오 사용자 정보를 복원한다.
     * providerUserId 클레임이 없으면 subject에서 꺼냄(구버전 토큰 호환).
     */
    public KakaoSignupTokenPayload getKakaoSignupTokenPayload(String token) {
        Claims claims = parseClaims(token, TokenType.SIGNUP); // 서명/만료/타입 검증 후 클레임 추출
        String providerUserId = claims.get("providerUserId", String.class);
        if (providerUserId == null || providerUserId.isBlank()) {
            providerUserId = claims.getSubject(); // 구버전 토큰 폴백
        }

        return new KakaoSignupTokenPayload(
                providerUserId,
                claims.get("email", String.class),
                claims.get("nickname", String.class),
                claims.get("profileImageUrl", String.class)
        );
    }

    /**
     * [해석] 토큰 타입(expectedType)까지 검증한 뒤 사용자 publicId(subject)를 추출한다.
     */
    public String getPublicId(String token, TokenType expectedType) {
        Claims claims = parseClaims(token, expectedType);
        return claims.getSubject(); // subject에 publicId가 들어있음
    }

    /**
     * [해석] ACCESS 토큰의 관리자 권한(isAdmin) 클레임을 읽는다.
     */
    public boolean isAdmin(String accessToken) {
        Claims claims = parseClaims(accessToken, TokenType.ACCESS);
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        return Boolean.TRUE.equals(isAdmin); // null이면 false 처리
    }

    /** access 토큰 만료시간(초) 조회 — 로그인 응답에 만료정보를 내려줄 때 사용. */
    public long getAccessExpirationSeconds() {
        return jwtProperties.getAccessExpirationSeconds();
    }

    /** 지금부터 refresh 만료까지의 절대 시각 계산 — DB에 저장할 만료시각으로 사용. */
    public Instant getRefreshExpiryInstant() {
        return Instant.now().plusSeconds(jwtProperties.getRefreshExpirationSeconds());
    }

    /**
     * [내부 공통] 실제 JWT를 만드는 함수.
     * tokenType 클레임을 반드시 넣어 access/refresh/signup을 구분한다.
     *
     * [중요] jti(고유 ID)를 반드시 포함시킨다.
     *   JWT의 iat/exp는 "초 단위"라, jti가 없으면 같은 사용자에게 같은 초 안에 발급한 토큰이
     *   문자열까지 완전히 동일해진다. refresh 토큰은 SHA-256 해시로 DB에 저장되고
     *   token_hash에 유니크 제약이 걸려 있어, 중복 토큰은 INSERT 실패(500)로 이어졌다.
     *   jti를 넣으면 발급 시각과 무관하게 항상 서로 다른 토큰이 된다.
     */
    private String createToken(String publicId, TokenType tokenType, long expirationSeconds, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(publicId)                       // 토큰 주인(사용자 식별자)
                .claims(extraClaims)                      // 추가 클레임(isAdmin, 카카오 정보 등)
                .claim("tokenType", tokenType.name())    // 용도 구분 클레임
                .id(UUID.randomUUID().toString())        // jti: 매 발급마다 유일 → 초 단위 토큰 중복 방지
                .issuedAt(Date.from(now))                // 발급 시각
                .expiration(Date.from(now.plusSeconds(expirationSeconds))) // 만료 시각
                .signWith(secretKey)                      // 시크릿으로 서명(위조 방지)
                .compact();                               // 최종 문자열로 직렬화
    }

    /**
     * [내부 공통] JWT 서명/만료/토큰 타입을 검증하고 클레임을 반환한다.
     * 검증 실패는 모두 UNAUTHORIZED 로 변환한다.
     */
    private Claims parseClaims(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)         // 서명 검증(시크릿 일치 확인)
                    .build()
                    .parseSignedClaims(token)      // 파싱(만료면 여기서 예외)
                    .getPayload();
            String tokenType = claims.get("tokenType", String.class);
            if (!expectedType.name().equals(tokenType)) { // 기대한 용도와 다르면 거부
                throw new ApiException(ErrorCode.UNAUTHORIZED, "토큰 타입이 올바르지 않습니다.");
            }
            return claims;
        } catch (ApiException exception) {
            throw exception; // 우리가 던진 타입 불일치 예외는 그대로 전달
        } catch (Exception exception) {
            // 서명 불일치/만료/형식오류 등 모든 라이브러리 예외를 401로 통일
            throw new ApiException(ErrorCode.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }

    /**
     * [내부 헬퍼] 값이 있을 때만 클레임 맵에 넣는다(null 클레임 방지).
     */
    private void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        claims.put(key, value);
    }
}
