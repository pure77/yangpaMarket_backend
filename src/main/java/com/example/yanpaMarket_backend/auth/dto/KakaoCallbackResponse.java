package com.example.yanpaMarket_backend.auth.dto;

public record KakaoCallbackResponse(
        boolean requiresProfileSetup,
        String signupToken,
        String userId,
        String email,
        String nickname,
        String accessToken,
        String refreshToken,
        Long expiresIn
) {
    public static KakaoCallbackResponse profileSetupRequired(String signupToken, String userId, String email, String nickname) {
        return new KakaoCallbackResponse(true, signupToken, userId, email, nickname, null, null, null);
    }

    public static KakaoCallbackResponse authenticated(TokenResponse tokenResponse) {
        return new KakaoCallbackResponse(
                false,
                null,
                tokenResponse.userId(),
                null,
                null,
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.expiresIn()
        );
    }
}
