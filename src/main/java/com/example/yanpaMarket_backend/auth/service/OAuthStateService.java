package com.example.yanpaMarket_backend.auth.service; // auth.service = 인증 비즈니스 로직 계층

import com.example.yanpaMarket_backend.config.properties.JwtProperties; // 서명에 쓸 시크릿 재사용
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;     // 예측 불가능한 난수 생성기
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;               // HMAC 서명 계산기
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

import static java.security.MessageDigest.isEqual; // 상수 시간 비교(타이밍 공격 방지)

/**
 * [무엇] 카카오 OAuth의 CSRF 공격을 막기 위한 "state" 값을 발급/검증하는 서비스.
 * [동작 방식] state = nonce.expiresAt.HMAC(nonce.expiresAt)
 *   - nonce: 24바이트 랜덤 값 (Base64url)
 *   - expiresAt: 발급 시각 + 5분 (Unix 초)
 *   - 서명: JWT 시크릿 기반 HMAC-SHA256
 * [장점] DB/캐시를 쓰지 않으므로 서버 재시작에도 영향 없음(자기 검증형 토큰).
 * [연결]
 *   - issueState(): 로그인 URL 발급(KakaoLoginUrlResponse)에 사용.
 *   - validateAndConsume(): 콜백(KakaoCallbackRequest.state) 검증에 사용.
 */
@Component
public class OAuthStateService {

    private static final Duration TTL = Duration.ofMinutes(5); // state 유효 시간 (5분)
    private static final String HMAC_ALGORITHM = "HmacSHA256";  // 서명 알고리즘
    private static final SecureRandom RANDOM = new SecureRandom(); // 암호학적 난수 생성기

    private final JwtProperties jwtProperties;                       // 서명 시크릿 제공
    private final java.time.Clock clock = java.time.Clock.systemUTC(); // 현재 시각 공급(테스트 용이성)

    public OAuthStateService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * [발급] 서명된 state 값을 만든다. 형식: "{nonce}.{expiresAt}.{signature}"
     */
    public String issueState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes); // 위조 불가능한 랜덤 nonce 바이트 생성

        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // URL 안전 문자열로 인코딩
        long expiresAt = Instant.now(clock).plus(TTL).getEpochSecond(); // 5분 후 만료시각(Unix 초)
        String payload = nonce + "." + expiresAt; // 서명 대상 본문
        String signature = sign(payload);          // 본문 서명 → 위조 방지

        return payload + "." + signature; // 본문 + 서명 결합
    }

    /**
     * [검증] state의 서명과 만료를 확인한다.
     *   - 서명 불일치 → 위조/CSRF 시도
     *   - 만료 초과 → 5분 안에 콜백이 안 옴
     * @return 유효하면 true
     */
    public boolean validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        // state 를 nonce / expiresAt / signature 세 조각으로 분리
        String[] parts = state.split("\\.");
        if (parts.length != 3) {
            return false; // 형식이 다르면 무효
        }

        // 본문을 다시 서명해 보고, 전달된 서명과 상수 시간으로 비교(타이밍 공격 방지)
        String payload = parts[0] + "." + parts[1];
        String expectedSignature = sign(payload);
        if (!isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return false; // 서명 불일치 → 위조된 state
        }

        // 만료 시각 파싱 및 검증
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            return false; // 숫자가 아니면 무효
        }

        return expiresAt > Instant.now(clock).getEpochSecond(); // 아직 만료 전이면 통과
    }

    /**
     * [내부] JWT 시크릿으로 payload를 HMAC-SHA256 서명한 뒤 Base64url 문자열로 반환.
     */
    private String sign(String payload) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured for OAuth state signing.");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);                                       // 서명기 생성
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM)); // 시크릿 키 주입
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));        // 서명 계산
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);       // URL 안전 문자열로 인코딩
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign OAuth state.", exception);
        }
    }
}
