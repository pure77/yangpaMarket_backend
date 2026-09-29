package com.example.yanpaMarket_backend.auction.dto;

/** WS broadcast: 입찰 갱신. remainingTime은 종료까지 남은 초. */
public record BidUpdateMessage(
        String type,
        long currentPrice,
        int bidCount,
        long remainingTime,
        String maskedBidder
) {
    public static BidUpdateMessage of(long currentPrice, int bidCount, long remainingTime, String maskedBidder) {
        return new BidUpdateMessage("BID_UPDATE", currentPrice, bidCount, remainingTime, maskedBidder);
    }
}
