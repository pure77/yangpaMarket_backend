package com.example.yanpaMarket_backend.auth.service; // auth.service = 인증 비즈니스 로직 계층

import com.example.yanpaMarket_backend.user.domain.User;
import java.time.Instant;

/**
 * [무엇] 리프레시 토큰을 "저장/검증/폐기"하는 기능의 인터페이스(추상 규약).
 * [왜 인터페이스?]
 *   - 저장 방식(현재는 MySQL)을 구현체로 분리해, 추후 Redis 등으로 바꿔도 호출부 영향이 없게 함.
 * [연결]
 *   - 구현체: MySqlRefreshTokenStore.
 *   - 사용처: AuthService(로그인/토큰갱신/로그아웃 흐름).
 */
public interface RefreshTokenStore {
    void replace(User user, String refreshToken, Instant expiresAt); // 기존 토큰 폐기 후 새 토큰 1개만 저장

    boolean isValid(User user, String refreshToken); // 해당 사용자의 토큰이 유효한지(존재/미폐기/미만료) 검증

    void revoke(String refreshToken); // 특정 토큰 1개 폐기 (로그아웃)

    void revokeAll(User user); // 해당 사용자의 모든 토큰 폐기 (전체 로그아웃/보안 조치)
}
