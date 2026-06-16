package com.example.yanpaMarket_backend.auth.repository; // auth.repository = 인증 도메인 DB 접근 계층

import com.example.yanpaMarket_backend.auth.domain.AuthProvider;
import com.example.yanpaMarket_backend.auth.domain.UserSocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 소셜 계정 연결정보(UserSocialAccount)의 DB 접근 Repository.
 * [연결]
 *   - AuthService 가 "이 카카오 계정이 이미 가입돼 있나"를 판단할 때 사용.
 */
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {
    Optional<UserSocialAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId); // 제공자+제공자ID로 기존 연결 조회(로그인 매칭)
    Optional<UserSocialAccount> findByUserIdAndProvider(Long userId, AuthProvider provider);                  // 특정 사용자의 특정 제공자 연결 조회
}
