package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

import jakarta.validation.constraints.NotBlank;

/**
 * [무엇] 토큰 갱신 요청 바디 DTO.
 * [어떻게 쓰임]
 *   - POST /api/v1/auth/refresh 의 @RequestBody.
 *   - 만료된 액세스 토큰 대신 리프레시 토큰으로 새 토큰 묶음을 받을 때 사용.
 * [연결]
 *   - AuthService 가 이 토큰을 검증하고 새 TokenResponse 를 발급.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken // 클라이언트가 보관 중인 리프레시 토큰 원문
) {
}
