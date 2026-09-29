package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 요청/응답 데이터 객체

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [무엇] dev 전용 테스트 로그인 요청 바디.
 *        카카오 없이 닉네임만으로 테스트 계정을 확보하기 위한 최소 입력.
 * [주의] 운영(prod)에서는 DevAuthController 자체가 로드되지 않으므로 사용 불가.
 *
 * record = 불변 데이터 운반 객체 (닉네임 하나만 담는다).
 */
public record DevLoginRequest(
        // 테스트 계정 식별에 쓰는 닉네임 (User.nickname 컬럼 길이 20 제약과 맞춤)
        @NotBlank(message = "nickname은 필수입니다.")
        @Size(max = 20, message = "nickname은 최대 20자입니다.")
        String nickname
) {
}
