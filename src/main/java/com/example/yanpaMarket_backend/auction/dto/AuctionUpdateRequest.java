package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 수정 요청 DTO.
 *
 * 입찰 0건인 경우에만 수정이 허용된다 (Auction.isModifiable() 검사).
 * 수정 가능한 모든 필드를 전체 교체(PUT 방식)로 처리한다.
 *
 * - imageIds: 새 이미지 목록으로 교체 (기존 연결 해제 후 재연결)
 */
public record AuctionUpdateRequest(
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
