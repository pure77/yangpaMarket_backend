package com.example.yanpaMarket_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TermsAgreementRequest(
        @NotBlank(message = "약관 코드는 필수입니다.")
        String termCode,
        boolean isRequired,
        boolean agreed
) {
}
