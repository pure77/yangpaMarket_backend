package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import jakarta.validation.constraints.NotBlank; // 빈 문자열/null 금지
import jakarta.validation.constraints.NotNull;  // null 금지
import jakarta.validation.constraints.Positive; // 0보다 커야 함
import java.time.LocalDateTime;
import java.util.List;

/**
 * [무엇] 경매 "등록" 요청 바디 DTO.
 * [어떻게 쓰임]
 *   - POST /api/v1/auctions 의 @RequestBody.
 * [연결]
 *   - AuctionService.create() 가 이 값으로 Auction 엔티티를 만든다.
 *   - 검증 실패는 GlobalExceptionHandler가 VALIDATION_ERROR(400)로 응답.
 */
public record AuctionCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,                       // 경매 제목

        @NotBlank(message = "설명은 필수입니다.")
        String description,                 // 상품 설명

        @NotNull(message = "카테고리는 필수입니다.")
        AuctionCategory category,           // 카테고리(enum)

        @NotNull(message = "상품 상태는 필수입니다.")
        AuctionItemCondition condition,     // 상품 상태(enum)

        @NotNull(message = "시작가는 필수입니다.")
        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startPrice,                    // 시작가(0 이하 불가)

        Long buyNowPrice,                   // 즉시구매가 (null이면 즉시구매 불가)

        @NotNull(message = "마감 시간은 필수입니다.")
        LocalDateTime endTime,              // 경매 마감 시각

        List<String> imageIds               // 업로드된 이미지들의 public_id 목록(선택, 순서대로 처리)
) {
}
