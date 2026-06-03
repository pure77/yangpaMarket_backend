package com.example.yanpaMarket_backend.auth.service;

import com.example.yanpaMarket_backend.auth.domain.AuthRefreshToken;
import com.example.yanpaMarket_backend.auth.repository.AuthRefreshTokenRepository;
import com.example.yanpaMarket_backend.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class MySqlRefreshTokenStore implements RefreshTokenStore {

    private final AuthRefreshTokenRepository authRefreshTokenRepository;

    /**
     * [세션 교체]
     * 로그인/재발급 시 기존 active refresh token을 모두 폐기하고
     * 새 refresh token 1개만 활성 상태로 저장합니다.
     */
    @Override
    public void replace(User user, String refreshToken, Instant expiresAt) {
        revokeAll(user);
        authRefreshTokenRepository.save(
                AuthRefreshToken.builder()
                        .user(user)
                        .tokenHash(hash(refreshToken))
                        .expiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
                        .revoked(false)
                        .build()
        );
    }

    /**
     * [refresh 유효성 확인]
     * 검증 조건:
     * - 토큰 해시 존재
     * - 폐기되지 않음
     * - 요청 사용자와 동일
     * - 만료되지 않음
     */
    @Override
    public boolean isValid(User user, String refreshToken) {
        return authRefreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(token -> {
                    token.markUsedNow();
                    return true;
                })
                .orElse(false);
    }

    /**
     * [단일 refresh 폐기]
     */
    @Override
    public void revoke(String refreshToken) {
        authRefreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(AuthRefreshToken::revoke);
    }

    /**
     * [사용자 전체 refresh 폐기]
     * 로그아웃/재로그인 시 사용자와 연결된 모든 active refresh token을 무효화합니다.
     */
    @Override
    public void revokeAll(User user) {
        List<AuthRefreshToken> activeTokens = authRefreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        activeTokens.forEach(AuthRefreshToken::revoke);
    }

    /**
     * DB에는 원문 refresh token을 저장하지 않고 SHA-256 해시만 저장합니다.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash refresh token.", exception);
        }
    }
}
