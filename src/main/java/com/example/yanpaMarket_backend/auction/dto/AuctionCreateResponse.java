package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import java.time.LocalDateTime;

/**
 * 경매 등록 완료 응답 DTO.
 *
 * - auctionId: 외부 노출용 public_id
 * - title: 등록된 경매 제목
 * - status: 경매 상태 (등록 직후 "ACTIVE")
 * - createdAt: 생성 시각 (BaseTimeEntity 로부터 상속)
 */
public record AuctionCreateResponse(
        String auctionId,
        String title,
        String status,
        LocalDateTime createdAt
) {

    /**
     * Auction 엔티티로부터 응답 DTO를 생성한다.
     *
     * @param auction 저장 완료된 Auction 엔티티
     * @return 등록 완료 응답 객체
     */
    public static AuctionCreateResponse from(Auction auction) {
        return new AuctionCreateResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getStatus().name(),
                auction.getCreatedAt()
        );
    }
}
