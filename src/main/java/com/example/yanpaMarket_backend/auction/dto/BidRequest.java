package com.example.yanpaMarket_backend.auction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 입찰 요청 본문. */
public record BidRequest(
        @NotNull(message = "입찰 금액은 필수입니다.")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        Long amount
) {
}
