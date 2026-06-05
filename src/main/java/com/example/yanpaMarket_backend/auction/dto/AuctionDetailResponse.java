package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 상세 조회 응답 DTO.
 *
 * - images: 정렬된 이미지 URL 배열 (AuctionImage.sortOrder 순)
 * - seller: 판매자 요약 정보 (userId=public_id, nickname, profileImage)
 * - startTime: 경매 공개 시각 (등록 시각 + 5분 유예 후)
 * - endTime: 경매 마감 시각
 * - condition: 상품 상태 (NEW / LIKE_NEW / USED 등)
 */
public record AuctionDetailResponse(
        String auctionId,
        String title,
        String description,
        List<String> images,
        Long startPrice,
        Long currentPrice,
        Long buyNowPrice,
        Integer bidCount,
        LocalDateTime endTime,
        LocalDateTime startTime,
        String status,
        String category,
        String condition,
        Seller seller
) {

    /**
     * 판매자 요약 정보 내부 레코드.
     *
     * - userId: User 엔티티의 public_id
     * - nickname: 사용자 닉네임
     * - profileImage: 프로필 이미지 URL (없으면 null)
     */
    public record Seller(String userId, String nickname, String profileImage) {
    }

    /**
     * Auction 엔티티, 이미지 URL 목록, 판매자 User 엔티티로부터 상세 응답 DTO를 생성한다.
     *
     * @param auction   조회된 Auction 엔티티
     * @param imageUrls 정렬된 이미지 URL 목록
     * @param seller    판매자 User 엔티티
     * @return 경매 상세 응답 객체
     */
    public static AuctionDetailResponse from(Auction auction, List<String> imageUrls, User seller) {
        return new AuctionDetailResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getDescription(),
                imageUrls,
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.getBuyNowPrice(),
                auction.getBidCount(),
                auction.getEndAt(),
                auction.getStartAt(),
                auction.getStatus().name(),
                auction.getCategory().name(),
                auction.getItemCondition().name(),
                new Seller(
                        seller.getPublicId(),
                        seller.getNickname(),
                        seller.getProfileImageUrl()
                )
        );
    }
}
