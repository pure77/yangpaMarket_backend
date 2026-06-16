package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.auction.domain.Auction;
import java.time.LocalDateTime;

/**
 * [무엇] 경매 "등록 완료" 응답 DTO.
 * [어떻게 쓰임]
 *   - POST /api/v1/auctions 의 응답 데이터.
 * [연결]
 *   - AuctionService.create() 가 저장된 Auction 엔티티를 from()으로 변환해 반환.
 */
public record AuctionCreateResponse(
        String auctionId,        // 외부 노출용 식별자(public_id)
        String title,            // 등록된 제목
        String status,           // 경매 상태(등록 직후 "ACTIVE")
        LocalDateTime createdAt  // 생성 시각(BaseTimeEntity에서 상속)
) {

    /**
     * [변환 팩토리] 저장된 Auction 엔티티 → 등록 완료 응답으로 매핑.
     */
    public static AuctionCreateResponse from(Auction auction) {
        return new AuctionCreateResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getStatus().name(), // enum → 문자열
                auction.getCreatedAt()
        );
    }
}
