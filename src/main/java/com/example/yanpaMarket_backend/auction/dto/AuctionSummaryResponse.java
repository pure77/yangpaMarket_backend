package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.auction.domain.Auction;
import java.time.LocalDateTime;

/**
 * [무엇] 경매 목록의 "카드 1건" 응답 DTO (목록에서 간략히 보여줄 정보).
 * [어떻게 쓰임]
 *   - AuctionListResponse.content 배열의 각 항목.
 * [연결]
 *   - AuctionService 가 from()으로 Auction → 카드로 변환.
 */
public record AuctionSummaryResponse(
        String auctionId,          // 외부 노출 식별자(public_id)
        String title,              // 제목
        Long currentPrice,         // 현재 최고가(입찰 없으면 시작가와 동일)
        Long buyNowPrice,          // 즉시구매가(null이면 불가)
        Integer bidCount,          // 입찰 건수
        LocalDateTime endTime,     // 마감 시각(end_at)
        LocalDateTime startTime,   // 공개 예정 시각(start_at)
        String status,             // 경매 상태 문자열
        String category,           // 카테고리 문자열
        String sellerId,           // 판매자 publicId (공개 목록은 null 가능)
        String thumbnailUrl        // 대표 이미지 URL(없으면 null)
) {

    /**
     * [변환 팩토리] Auction + 썸네일 URL + 판매자 ID → 목록 카드로 매핑.
     * @param thumbnailUrl 대표 이미지 URL(없으면 null)
     * @param sellerId     판매자 publicId(공개 목록은 null, 내 경매 목록은 본인 ID)
     */
    public static AuctionSummaryResponse from(Auction auction, String thumbnailUrl, String sellerId) {
        return new AuctionSummaryResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getCurrentPrice(),
                auction.getBuyNowPrice(),
                auction.getBidCount(),
                auction.getEndAt(),
                auction.getStartAt(),
                auction.getStatus().name(),   // enum → 문자열
                auction.getCategory().name(), // enum → 문자열
                sellerId,
                thumbnailUrl
        );
    }
}
