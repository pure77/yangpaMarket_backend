package com.example.yanpaMarket_backend.auction.precheck;

import java.time.LocalDateTime;

/**
 * [무엇] 입찰 커밋 직후 Redis에 기록할 값 묶음. 트랜잭션 안에서 갱신된 Auction으로 만든다.
 * [왜 한 번에] 필드 일부만 있는 해시가 생기지 않도록 항상 네 값을 같이 쓴다.
 */
public record BidPriceSnapshot(long currentPrice, long minimumBidIncrement, Long sellerUserId, LocalDateTime endAt) {
}
