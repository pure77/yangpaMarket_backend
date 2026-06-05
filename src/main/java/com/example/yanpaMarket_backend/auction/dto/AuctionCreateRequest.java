package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 등록 요청 DTO.
 *
 * - title/description/category/condition/startPrice/endTime 은 필수값
 * - buyNowPrice: null이면 즉시구매 불가
 * - imageIds: 업로드된 이미지들의 public_id 목록 (선택, 순서대로 처리)
 *
 * [유효성 검사]
 * - @NotBlank: 빈 문자열/공백 방지
 * - @NotNull + @Positive: 시작가 0 이하 입력 방지
 */
public record AuctionCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "설명은 필수입니다.")
        String description,

        @NotNull(message = "카테고리는 필수입니다.")
        AuctionCategory category,

        @NotNull(message = "상품 상태는 필수입니다.")
        AuctionItemCondition condition,

        @NotNull(message = "시작가는 필수입니다.")
        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startPrice,

        Long buyNowPrice,

        @NotNull(message = "마감 시간은 필수입니다.")
        LocalDateTime endTime,

        List<String> imageIds
) {
}
