package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

import jakarta.validation.constraints.NotBlank;

/**
 * [무엇] 약관 1건에 대한 동의 정보 DTO.
 * [어떻게 쓰임]
 *   - SignupCompleteRequest.agreements 리스트의 각 항목으로 사용.
 * [연결]
 *   - AuthService 가 이 값으로 UserTermsAgreement 엔티티를 만들어 저장.
 */
public record TermsAgreementRequest(
        @NotBlank(message = "약관 코드는 필수입니다.")
        String termCode,    // 약관 식별 코드 (예: TERMS_OF_SERVICE)
        boolean isRequired, // 필수 약관 여부
        boolean agreed      // 동의 여부
) {
}
