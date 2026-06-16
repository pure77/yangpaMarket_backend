package com.example.yanpaMarket_backend.auction.repository; // auction.repository = 경매 DB 접근 계층

import com.example.yanpaMarket_backend.auction.domain.AuctionImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 경매-이미지 매핑(AuctionImage)의 DB 접근 Repository.
 * [연결]
 *   - AuctionService 가 경매의 이미지 목록 조회/교체에 사용.
 */
public interface AuctionImageRepository extends JpaRepository<AuctionImage, Long> {
    List<AuctionImage> findByAuctionIdOrderBySortOrderAsc(Long auctionId); // 특정 경매의 이미지들을 정렬순으로 조회
    void deleteByAuctionId(Long auctionId);                                // 특정 경매의 이미지 연결 전체 삭제(수정 시 재연결 전 정리)
}
