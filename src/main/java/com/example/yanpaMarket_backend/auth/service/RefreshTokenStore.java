package com.example.yanpaMarket_backend.auth.service;

import com.example.yanpaMarket_backend.user.domain.User;
import java.time.Instant;

public interface RefreshTokenStore {
    void replace(User user, String refreshToken, Instant expiresAt);

    boolean isValid(User user, String refreshToken);

    void revoke(String refreshToken);

    void revokeAll(User user);
}
