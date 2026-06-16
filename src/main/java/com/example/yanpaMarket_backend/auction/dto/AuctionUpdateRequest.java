package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [무엇] 경매 "수정" 요청 바디 DTO.
 * [규칙] 입찰이 0건일 때만 수정 허용 (Auction.isModifiable() 검사).
 *        수정 가능한 모든 필드를 전체 교체(PUT 방식)한다.
 * [어떻게 쓰임]
 *   - PUT /api/v1/auctions/{id} 의 @RequestBody.
 * [연결]
 *   - AuctionService.update() 가 처리. imageIds 는 기존 연결 해제 후 새 목록으로 재연결.
 */
public record AuctionUpdateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,                   // 수정할 제목

        @NotBlank(message = "설명은 필수입니다.")
        String description,             // 수정할 설명

        @NotNull(message = "카테고리는 필수입니다.")
        AuctionCategory category,       // 수정할 카테고리

        @NotNull(message = "상품 상태는 필수입니다.")
        AuctionItemCondition condition, // 수정할 상품 상태

        @NotNull(message = "시작가는 필수입니다.")
        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startPrice,                // 수정할 시작가(변경 시 현재가도 재설정됨)

        Long buyNowPrice,               // 수정할 즉시구매가(null이면 불가)

        @NotNull(message = "마감 시간은 필수입니다.")
        LocalDateTime endTime,          // 수정할 마감 시각

        List<String> imageIds           // 교체할 이미지 목록(기존 연결 해제 후 재연결)
) {
}
