package com.example.yanpaMarket_backend.auction.domain; // auction.domain = 경매 도메인 패키지

/**
 * [무엇] 경매 상품의 "상태(중고 정도)"를 나타내는 enum (DB 스키마 item_condition ENUM과 동일).
 * [연결]
 *   - Auction.itemCondition 컬럼에 문자열로 저장된다.
 */
public enum AuctionItemCondition {
    UNUSED,   // 미사용(새 상품)
    LIKE_NEW, // 거의 새것
    USED      // 사용감 있음
}
