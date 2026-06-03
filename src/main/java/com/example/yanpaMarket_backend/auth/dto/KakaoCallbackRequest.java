package com.example.yanpaMarket_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoCallbackRequest(
        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,
        @NotBlank(message = "state 값은 필수입니다.")
        String state,
        @NotBlank(message = "redirectUri는 필수입니다.")
        String redirectUri
) {
}
