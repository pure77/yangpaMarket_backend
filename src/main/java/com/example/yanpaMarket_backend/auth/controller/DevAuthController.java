package com.example.yanpaMarket_backend.auth.controller; // auth.controller = 인증 HTTP 요청 처리 계층

import com.example.yanpaMarket_backend.auth.dto.DevLoginRequest;
import com.example.yanpaMarket_backend.auth.dto.TokenResponse;
import com.example.yanpaMarket_backend.auth.service.AuthService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [무엇] "dev 프로필 전용" 테스트 로그인 컨트롤러.
 *        카카오 OAuth 없이 닉네임만으로 진짜 access/refresh 토큰을 발급받아,
 *        실시간 입찰 등 인증이 필요한 기능을 여러 계정으로 손쉽게 테스트하기 위한 도구.
 * [안전장치]
 *   - @Profile("dev"): dev 프로필이 활성일 때만 스프링 빈으로 등록된다.
 *     운영(prod) 등 dev가 없는 환경에서는 클래스 자체가 로드되지 않아 엔드포인트가 존재하지 않는다.
 * [연결]
 *   - AuthService.devLogin(): 테스트 User를 upsert하고 기존 issueTokens 로직으로 토큰 발급.
 *   - SecurityConfig: "/api/v1/auth/dev/login"을 공개 경로(PUBLIC_ENDPOINTS)에 등록해야 접근 가능.
 */
@Profile("dev") // dev 프로필에서만 활성화 (운영에서는 아예 로드되지 않음)
@RestController
@RequestMapping("/api/v1/auth/dev")
@RequiredArgsConstructor
public class DevAuthController {

    private final AuthService authService; // 인증 비즈니스 로직 위임 대상(생성자 주입)

    /**
     * [테스트 로그인]
     * 닉네임만 받아 해당 테스트 계정을 확보(없으면 생성)하고 실제 로그인 토큰을 발급한다.
     * 서로 다른 닉네임으로 여러 번 호출하면 여러 테스트 계정을 만들 수 있다.
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> devLogin(@Valid @RequestBody DevLoginRequest request) {
        return ApiResponse.success(authService.devLogin(request.nickname()));
    }
}
