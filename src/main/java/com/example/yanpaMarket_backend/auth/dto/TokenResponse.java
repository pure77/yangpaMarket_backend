package com.example.yanpaMarket_backend.auth.dto;

public record TokenResponse(
        String userId,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
