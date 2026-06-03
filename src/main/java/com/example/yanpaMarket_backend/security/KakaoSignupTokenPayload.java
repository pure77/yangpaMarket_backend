package com.example.yanpaMarket_backend.security;

public record KakaoSignupTokenPayload(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
