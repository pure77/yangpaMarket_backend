package com.example.yanpaMarket_backend.user.controller;

import com.example.yanpaMarket_backend.global.api.ApiResponse;
import com.example.yanpaMarket_backend.user.dto.UserMeResponse;
import com.example.yanpaMarket_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 조회 컨트롤러.
 * 인증 완료 사용자의 마이페이지성 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 현재 인증된 사용자 본인 정보를 조회합니다.
     * JwtAuthenticationFilter가 저장한 principal(publicId)을 기준으로 조회합니다.
     */
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(Authentication authentication) {
        String publicId = (String) authentication.getPrincipal();
        return ApiResponse.success(UserMeResponse.from(userService.getByPublicId(publicId)));
    }
}
