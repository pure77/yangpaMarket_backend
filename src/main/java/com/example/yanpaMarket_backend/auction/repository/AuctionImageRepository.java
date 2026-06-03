package com.example.yanpaMarket_backend.auction.repository;

import com.example.yanpaMarket_backend.auction.domain.AuctionImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionImageRepository extends JpaRepository<AuctionImage, Long> {
    List<AuctionImage> findByAuctionIdOrderBySortOrderAsc(Long auctionId);
    void deleteByAuctionId(Long auctionId);
}
