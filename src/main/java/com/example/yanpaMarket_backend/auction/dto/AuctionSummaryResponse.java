package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import java.time.LocalDateTime;

/**
 * 경매 목록 카드 1건 응답 DTO.
 *
 * - auctionId: 외부 노출용 public_id
 * - currentPrice: 현재 최고 입찰가 (없으면 시작가와 동일)
 * - bidCount: 현재까지 접수된 입찰 건수
 * - thumbnailUrl: 대표 이미지 URL (없으면 null)
 *
 * [사용처]
 * AuctionListResponse의 content 배열 항목으로 사용된다.
 */
public record AuctionSummaryResponse(
        String auctionId,
        String title,
        Long currentPrice,
        Long buyNowPrice,
        Integer bidCount,
        LocalDateTime endTime,
        String status,
        String category,
        String thumbnailUrl
) {

    /**
     * Auction 엔티티와 대표 이미지 URL로부터 목록 카드 DTO를 생성한다.
     *
     * @param auction      조회된 Auction 엔티티
     * @param thumbnailUrl 대표 이미지 URL (없으면 null 전달)
     * @return 목록 카드 응답 객체
     */
    public static AuctionSummaryResponse from(Auction auction, String thumbnailUrl) {
        return new AuctionSummaryResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getCurrentPrice(),
                auction.getBuyNowPrice(),
                auction.getBidCount(),
                auction.getEndAt(),
                auction.getStatus().name(),
                auction.getCategory().name(),
                thumbnailUrl
        );
    }
}
