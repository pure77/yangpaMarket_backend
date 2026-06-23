package com.example.yanpaMarket_backend.auction.dto;

/** WS broadcast: 경매 종료. winnerId는 낙찰자 publicId (입찰 없으면 null). */
public record AuctionEndedMessage(String type, long finalPrice, String winnerId) {

    public static AuctionEndedMessage of(long finalPrice, String winnerId) {
        return new AuctionEndedMessage("AUCTION_ENDED", finalPrice, winnerId);
    }
}
