package com.example.yanpaMarket_backend.auction.dto;

import java.time.LocalDateTime;

/** 입찰 성공 REST 응답. */
public record BidResponse(
        String bidId,
        long price,
        long currentHighestPrice,
        int bidCount,
        LocalDateTime createdAt
) {
    public static BidResponse of(
            String bidPublicId, long price, long currentHighestPrice, int bidCount, LocalDateTime createdAt) {
        return new BidResponse(bidPublicId, price, currentHighestPrice, bidCount, createdAt);
    }
}
