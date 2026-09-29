package com.example.yanpaMarket_backend.security; // 테스트 대상(JwtProvider)과 같은 패키지

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.config.properties.JwtProperties;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * [무엇] 발급되는 토큰이 "매번 서로 다른 문자열"인지 검증하는 회귀 테스트.
 * [배경] refresh 토큰은 SHA-256 해시로 auth_refresh_tokens 테이블에 저장되며,
 *        token_hash 에 유니크 제약(uk_auth_refresh_tokens_hash)이 걸려 있다.
 *        JWT의 iat/exp는 "초 단위"라, 같은 사용자에게 같은 초 안에 두 번 발급하면
 *        페이로드가 완전히 같아져 토큰 문자열까지 동일해진다.
 *        → 같은 해시를 두 번 INSERT → 유니크 제약 위반 → 500.
 * [보장] 토큰마다 고유한 jti가 들어가면 같은 초에 발급해도 항상 다른 문자열이 된다.
 */
class JwtProviderTokenUniquenessTest {

    // 테스트용 JwtProvider 생성 (시크릿은 32자 이상이어야 생성자 검증을 통과)
    private JwtProvider createProvider() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-for-unit-test-at-least-32-chars");
        properties.setAccessExpirationSeconds(86400);
        properties.setRefreshExpirationSeconds(604800);
        properties.setSignupExpirationSeconds(600);
        return new JwtProvider(properties);
    }

    @Test
    void 같은_초에_연속_발급한_refresh_토큰은_서로_달라야_한다() {
        JwtProvider jwtProvider = createProvider();

        // 같은 사용자에게 연속 두 번 발급 → 사실상 동일한 초에 발급된다
        String first = jwtProvider.createRefreshToken("test-public-id");
        String second = jwtProvider.createRefreshToken("test-public-id");

        // 동일하면 해시가 겹쳐 DB 유니크 제약 위반(500)으로 이어진다
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void refresh_토큰을_여러번_발급해도_모두_고유해야_한다() {
        JwtProvider jwtProvider = createProvider();
        Set<String> issuedTokens = new HashSet<>();

        // 짧은 시간에 100번 발급 — 대부분 같은 초에 몰린다
        for (int i = 0; i < 100; i++) {
            issuedTokens.add(jwtProvider.createRefreshToken("test-public-id"));
        }

        // 중복이 하나라도 있으면 Set 크기가 100 미만이 된다
        assertThat(issuedTokens).hasSize(100);
    }

    @Test
    void 같은_초에_연속_발급한_access_토큰도_서로_달라야_한다() {
        JwtProvider jwtProvider = createProvider();

        String first = jwtProvider.createAccessToken("test-public-id", false);
        String second = jwtProvider.createAccessToken("test-public-id", false);

        assertThat(first).isNotEqualTo(second);
    }
}
