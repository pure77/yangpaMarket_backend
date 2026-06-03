package com.example.yanpaMarket_backend.auth.client;

public record KakaoUserInfo(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
