package com.example.yanpaMarket_backend.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record SignupCompleteRequest(
        @NotBlank(message = "signupToken은 필수입니다.")
        String signupToken,
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname,
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-0000-0000 이어야 합니다.")
        String phone,
        boolean marketingOptIn,
        @NotEmpty(message = "약관 동의 목록은 필수입니다.")
        List<@Valid TermsAgreementRequest> agreements
) {
}
