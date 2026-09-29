package com.example.yanpaMarket_backend.auction.repository;

import com.example.yanpaMarket_backend.auction.domain.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 입찰(Bid) 엔티티의 DB 접근 Repository.
 * - BidService 에서 입찰 저장 및 경매별 내역 조회에 사용.
 */
public interface BidRepository extends JpaRepository<Bid, Long> {

    /** 특정 경매의 입찰 내역을 최신순으로 페이징 조회. */
    Page<Bid> findByAuctionIdOrderByCreatedAtDesc(Long auctionId, Pageable pageable);
}
