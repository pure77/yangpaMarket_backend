package com.example.yanpaMarket_backend.auth.service; // auth.service = 인증 비즈니스 로직 계층

import com.example.yanpaMarket_backend.auth.domain.AuthRefreshToken;
import com.example.yanpaMarket_backend.auth.repository.AuthRefreshTokenRepository;
import com.example.yanpaMarket_backend.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;          // SHA-256 해시 계산
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [무엇] RefreshTokenStore 인터페이스의 "MySQL 저장" 구현체.
 *        리프레시 토큰을 DB(auth_refresh_tokens)에 해시로 저장/검증/폐기한다.
 * [보안 핵심] 원문 토큰은 절대 저장하지 않고 SHA-256 해시만 저장한다.
 * [연결]
 *   - AuthRefreshTokenRepository 로 실제 DB 작업.
 *   - AuthService 가 이 구현체를 RefreshTokenStore 타입으로 주입받아 사용.
 *   - @Transactional: 메서드 단위로 DB 트랜잭션을 묶는다(변경 감지로 revoke 등이 반영됨).
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MySqlRefreshTokenStore implements RefreshTokenStore {

    private final AuthRefreshTokenRepository authRefreshTokenRepository; // 토큰 DB 접근

    /**
     * [세션 교체] 로그인/재발급 시 기존 활성 토큰을 모두 폐기하고 새 토큰 1개만 저장한다.
     * → "한 계정당 활성 세션 1개" 정책.
     *
     * [정리] 폐기(revoked) 처리된 과거 행은 여기서 함께 삭제한다.
     *   폐기 표시만 하고 두면 로그인/갱신을 반복할수록 행이 무한히 쌓이고,
     *   token_hash 유니크 제약과 맞물려 잠재적 충돌 지점이 된다.
     *   결과적으로 사용자당 활성 토큰 1행만 유지된다.
     */
    @Override
    public void replace(User user, String refreshToken, Instant expiresAt) {
        revokeAll(user);                                                    // 기존 활성 토큰 전부 폐기
        authRefreshTokenRepository.flush();                                 // 폐기 상태를 DB에 먼저 반영
        authRefreshTokenRepository.deleteAllByUserIdAndRevokedTrue(user.getId()); // 폐기된 과거 행 정리
        authRefreshTokenRepository.save(
                AuthRefreshToken.builder()
                        .user(user)
                        .tokenHash(hash(refreshToken)) // 원문 대신 해시 저장
                        .expiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())) // Instant → LocalDateTime 변환
                        .revoked(false)
                        .build()
        );
    }

    /**
     * [유효성 검증] 아래 조건을 모두 만족해야 유효:
     *   - 토큰 해시가 DB에 존재
     *   - 폐기되지 않음
     *   - 요청 사용자와 소유자가 동일
     *   - 만료되지 않음
     * 유효하면 마지막 사용시각을 갱신하고 true 반환.
     */
    @Override
    public boolean isValid(User user, String refreshToken) {
        return authRefreshTokenRepository.findByTokenHash(hash(refreshToken)) // 해시로 조회
                .filter(token -> !token.isRevoked())                                 // 폐기 안 됨
                .filter(token -> token.getUser().getId().equals(user.getId()))       // 소유자 일치
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))  // 만료 전
                .map(token -> {
                    token.markUsedNow(); // 사용 시각 기록(트랜잭션 종료 시 UPDATE)
                    return true;
                })
                .orElse(false); // 하나라도 불만족이면 false
    }

    /**
     * [단일 폐기] 특정 리프레시 토큰 1개를 폐기(로그아웃).
     */
    @Override
    public void revoke(String refreshToken) {
        authRefreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(AuthRefreshToken::revoke); // 있으면 revoke() 호출
    }

    /**
     * [전체 폐기] 해당 사용자의 모든 활성 토큰 무효화(전체 로그아웃/재로그인 시).
     */
    @Override
    public void revokeAll(User user) {
        List<AuthRefreshToken> activeTokens = authRefreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        activeTokens.forEach(AuthRefreshToken::revoke); // 각각 폐기
    }

    /**
     * [내부] 원문 토큰을 SHA-256으로 해시한 뒤 Base64 문자열로 반환.
     * DB에는 이 해시값만 저장한다(원문 유출 방지).
     */
    private String hash(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8)); // 해시 계산
            return Base64.getEncoder().encodeToString(digest);                               // 문자열로 인코딩
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash refresh token.", exception);
        }
    }
}
