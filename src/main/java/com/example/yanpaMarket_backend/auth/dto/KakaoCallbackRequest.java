package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

import jakarta.validation.constraints.NotBlank; // 빈 문자열/null 금지 검증

/**
 * [무엇] 카카오 로그인 콜백 요청 바디 DTO.
 *        카카오 로그인 후 프론트가 받은 인가코드(code)와 state 를 백엔드로 전달할 때 사용.
 * [어떻게 쓰임]
 *   - POST /api/v1/auth/kakao/callback 의 @RequestBody.
 * [연결]
 *   - AuthService 가 code 로 카카오 토큰을 교환하고 사용자 정보를 가져온다.
 *   - @NotBlank: 값이 없으면 GlobalExceptionHandler가 VALIDATION_ERROR(400)로 응답.
 */
public record KakaoCallbackRequest(
        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,         // 카카오가 발급한 1회용 인가 코드
        @NotBlank(message = "state 값은 필수입니다.")
        String state,        // CSRF 방지용 상태값 (로그인 URL 발급 시 받은 값과 대조)
        @NotBlank(message = "redirectUri는 필수입니다.")
        String redirectUri   // 토큰 교환 시 카카오에 함께 보내는 콜백 주소
) {
}
