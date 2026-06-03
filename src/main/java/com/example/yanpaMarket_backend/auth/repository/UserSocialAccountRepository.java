package com.example.yanpaMarket_backend.auth.repository;

import com.example.yanpaMarket_backend.auth.domain.AuthProvider;
import com.example.yanpaMarket_backend.auth.domain.UserSocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {
    Optional<UserSocialAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
    Optional<UserSocialAccount> findByUserIdAndProvider(Long userId, AuthProvider provider);
}
