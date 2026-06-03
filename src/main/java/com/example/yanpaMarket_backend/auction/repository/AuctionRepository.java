package com.example.yanpaMarket_backend.auction.repository;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findByPublicId(String publicId);

    /**
     * 공개 목록: status + start_at<=now 필터에 카테고리/키워드(옵션)를 결합.
     * 파라미터가 null이면 해당 조건을 건너뛴다(동적 필터).
     */
    @Query("""
            select a from Auction a
            where a.status = :status
              and a.startAt <= :now
              and (:category is null or a.category = :category)
              and (:keyword is null or a.title like %:keyword%)
            """)
    Page<Auction> findPublicList(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            @Param("category") AuctionCategory category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /** 마이페이지: 본인이 등록한 모든 경매(유예/취소 포함) 최신순. */
    List<Auction> findBySellerUserIdOrderByCreatedAtDesc(Long sellerUserId);
}
