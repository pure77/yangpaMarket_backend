package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

/**
 * [무엇] 로그인/토큰갱신 성공 시 내려주는 토큰 묶음 DTO.
 * [어떻게 쓰임]
 *   - POST /api/v1/auth/refresh 응답, 그리고 KakaoCallbackResponse.authenticated()의 재료로 사용.
 * [연결]
 *   - AuthService 가 JwtProvider 로 토큰을 만든 뒤 이 객체로 묶어 반환.
 */
public record TokenResponse(
        String userId,       // 사용자 외부 식별자(publicId)
        String accessToken,  // API 인증용 액세스 토큰
        String refreshToken, // 세션 유지용 리프레시 토큰
        long expiresIn       // 액세스 토큰 만료까지 남은 시간(초)
) {
}
