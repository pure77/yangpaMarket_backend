package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [무엇] 경매 "상세 조회" 응답 DTO (상세 페이지에 필요한 모든 정보).
 * [어떻게 쓰임]
 *   - GET /api/v1/auctions/{id} 의 응답 데이터.
 * [연결]
 *   - AuctionService 가 경매 + 이미지목록 + 판매자 정보를 모아 from()으로 변환.
 */
public record AuctionDetailResponse(
        String auctionId,          // 외부 식별자(public_id)
        String title,              // 제목
        String description,        // 설명
        List<String> images,       // 정렬된 이미지 URL 배열(sortOrder 순)
        List<String> imageIds,     // images와 같은 순서의 이미지 publicId(수정 시 기존 이미지 식별용)
        Long startPrice,           // 시작가
        Long currentPrice,         // 현재가
        Long buyNowPrice,          // 즉시구매가(null이면 불가)
        Integer bidCount,          // 입찰 건수
        LocalDateTime endTime,     // 마감 시각
        LocalDateTime startTime,   // 공개 시각
        String status,             // 상태 문자열
        String category,           // 카테고리 문자열
        String condition,          // 상품 상태 문자열
        Seller seller              // 판매자 요약 정보(아래 중첩 record)
) {

    /**
     * [중첩 DTO] 판매자 요약 정보.
     * 상세 페이지에서 "판매자" 영역에 보여줄 최소 정보만 담는다.
     */
    public record Seller(
            String userId,       // 판매자 public_id
            String nickname,     // 닉네임
            String profileImage  // 프로필 이미지 URL(없으면 null)
    ) {
    }

    /**
     * [변환 팩토리] 경매 + 이미지 URL/ID 목록 + 판매자 → 상세 응답으로 매핑.
     * @param imageUrls 정렬된 이미지 URL 목록
     * @param imageIds  정렬된 이미지 publicId 목록(URL과 같은 순서)
     * @param seller    판매자 User 엔티티
     */
    public static AuctionDetailResponse from(
            Auction auction, List<String> imageUrls, List<String> imageIds, User seller) {
        return new AuctionDetailResponse(
                auction.getPublicId(),
                auction.getTitle(),
                auction.getDescription(),
                imageUrls,
                imageIds,
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.getBuyNowPrice(),
                auction.getBidCount(),
                auction.getEndAt(),
                auction.getStartAt(),
                auction.getStatus().name(),
                auction.getCategory().name(),
                auction.getItemCondition().name(),
                new Seller( // 판매자 정보 묶기
                        seller.getPublicId(),
                        seller.getNickname(),
                        seller.getProfileImageUrl()
                )
        );
    }
}
