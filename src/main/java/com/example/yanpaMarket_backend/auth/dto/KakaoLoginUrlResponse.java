package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

/**
 * [무엇] 카카오 로그인 시작 URL 응답 DTO.
 * [어떻게 쓰임]
 *   - GET /api/v1/auth/kakao/login 응답.
 *   - 프론트는 authorizeUrl 로 사용자를 보내고, state 는 콜백 검증용으로 보관.
 * [연결]
 *   - OAuthStateService 가 state 를 생성/검증한다.
 */
public record KakaoLoginUrlResponse(
        String authorizeUrl, // 사용자를 보낼 카카오 로그인(인가) 페이지 URL
        String state         // CSRF 방지용 상태값 (콜백에서 다시 검증)
) {
}
