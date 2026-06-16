package com.example.yanpaMarket_backend.user.controller; // user.controller = 사용자 HTTP 요청 처리 계층

import com.example.yanpaMarket_backend.global.api.ApiResponse;
import com.example.yanpaMarket_backend.user.dto.UserMeResponse;
import com.example.yanpaMarket_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication; // 현재 인증된 사용자 정보(필터가 채워둠)
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [무엇] 사용자 조회 REST 컨트롤러. 마이페이지성 본인 정보 조회 API를 제공.
 * [어떻게 쓰임]
 *   - 클라이언트의 HTTP 요청을 받아 서비스에 위임하고, 결과를 공통 응답 포맷으로 반환.
 * [연결]
 *   - @RequestMapping("/api/v1/users"): 이 컨트롤러의 모든 경로 앞에 붙는 공통 prefix.
 *   - UserService 로 조회, UserMeResponse 로 변환, ApiResponse 로 감싸 반환.
 *   - 인증 필요 경로(SecurityConfig 의 anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService; // 사용자 조회 서비스(생성자 주입)

    /**
     * [GET /api/v1/users/me] 현재 로그인한 사용자 본인 정보 조회.
     * @param authentication JwtAuthenticationFilter가 채워둔 인증 정보(principal=publicId)
     */
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(Authentication authentication) {
        String publicId = (String) authentication.getPrincipal();                 // 토큰에서 추출된 사용자 식별자
        return ApiResponse.success(UserMeResponse.from(userService.getByPublicId(publicId))); // 조회→DTO 변환→성공 응답
    }
}
