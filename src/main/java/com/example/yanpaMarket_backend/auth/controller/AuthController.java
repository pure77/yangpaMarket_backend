package com.example.yanpaMarket_backend.auth.controller;

import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackRequest;
import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackResponse;
import com.example.yanpaMarket_backend.auth.dto.KakaoLoginUrlResponse;
import com.example.yanpaMarket_backend.auth.dto.RefreshTokenRequest;
import com.example.yanpaMarket_backend.auth.dto.SignupCompleteRequest;
import com.example.yanpaMarket_backend.auth.dto.TokenResponse;
import com.example.yanpaMarket_backend.auth.service.AuthService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * [카카오 로그인 시작]
     * 1) 프론트가 이 API를 호출하면
     * 2) 서버가 authorizeUrl + state를 발급하고
     * 3) 프론트는 authorizeUrl로 브라우저를 이동시킵니다.
     */
    @GetMapping("/kakao/login")
    public ApiResponse<KakaoLoginUrlResponse> getKakaoLoginUrl() {
        return ApiResponse.success(authService.getKakaoLoginUrl());
    }

    /**
     * [카카오 콜백 처리]
     * 프론트 콜백 페이지가 전달한 code/state를 검증합니다.
     * - 기존 사용자: 즉시 access/refresh 토큰 발급
     * - 신규/미완성 사용자: signupToken 발급 후 추가 입력 단계로 분기
     */
    @PostMapping("/kakao/callback")
    public ApiResponse<KakaoCallbackResponse> kakaoCallback(@Valid @RequestBody KakaoCallbackRequest request) {
        return ApiResponse.success(authService.handleKakaoCallback(request));
    }

    /**
     * [추가 정보 입력 완료]
     * signupToken 기반으로 프로필/약관을 확정한 뒤 최종 로그인 토큰을 발급합니다.
     */
    @PostMapping("/signup/complete")
    public ApiResponse<TokenResponse> completeSignup(@Valid @RequestBody SignupCompleteRequest request) {
        return ApiResponse.success(authService.completeSignup(request));
    }

    /**
     * [토큰 재발급]
     * refreshToken 유효성 확인 후 access/refresh를 재발급합니다.
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    /**
     * [로그아웃]
     * 현재 사용자에게 연결된 refresh token들을 서버에서 무효화합니다.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        authService.logout((String) authentication.getPrincipal());
        return ApiResponse.successMessage("로그아웃 완료");
    }
}
