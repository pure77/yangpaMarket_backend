package com.example.yanpaMarket_backend.auth.repository;

import com.example.yanpaMarket_backend.auth.domain.AuthRefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

    List<AuthRefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
}
