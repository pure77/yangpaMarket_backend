package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

import jakarta.validation.Valid;                  // 중첩 객체(리스트 요소)도 검증하도록 지시
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;    // 리스트가 비어있으면 안 됨
import jakarta.validation.constraints.Pattern;     // 정규식 형식 검증
import java.util.List;

/**
 * [무엇] 회원가입 2단계(추가정보 입력) 완료 요청 바디 DTO.
 *        카카오 로그인 후 받은 signupToken 과 함께 닉네임/전화번호/약관 동의를 보낸다.
 * [어떻게 쓰임]
 *   - POST /api/v1/auth/signup/complete 의 @RequestBody.
 * [연결]
 *   - AuthService 가 signupToken 을 검증해 신규 User 를 생성하고 약관을 저장한 뒤 토큰을 발급.
 *   - 각 필드의 검증 실패는 VALIDATION_ERROR(400)로 응답.
 */
public record SignupCompleteRequest(
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,  // 회원가입 단계용 임시 토큰(카카오 정보 포함)
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname,     // 사용할 닉네임
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-0000-0000 이어야 합니다.")
        String phone,        // 전화번호 (정규식으로 형식 강제)
        boolean marketingOptIn, // 마케팅 수신 동의 여부
        @NotEmpty(message = "약관 동의 목록은 필수입니다.")
        List<@Valid TermsAgreementRequest> agreements // 약관 동의 목록(각 항목도 @Valid로 검증)
) {
}
