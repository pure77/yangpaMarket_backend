package com.example.yanpaMarket_backend.auth.repository; // auth.repository = 인증 도메인 DB 접근 계층

import com.example.yanpaMarket_backend.auth.domain.AuthRefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 리프레시 토큰(AuthRefreshToken)의 DB 접근 Repository.
 * [연결]
 *   - MySqlRefreshTokenStore 가 이 Repository로 토큰을 저장/조회/폐기한다.
 */
public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash); // 토큰 해시로 1건 조회(검증 시 사용)

    List<AuthRefreshToken> findAllByUserIdAndRevokedFalse(Long userId); // 특정 사용자의 "아직 폐기 안 된" 토큰 전부 조회(전체 폐기 시 사용)
}
