package com.example.yanpaMarket_backend.auction.domain; // auction.domain = 경매 도메인 엔티티/enum 패키지

/**
 * [무엇] 경매 상품의 "카테고리"를 나타내는 enum (DB 스키마 ENUM과 동일).
 * [연결]
 *   - Auction.category 컬럼에 문자열로 저장된다.
 *   - 한글 라벨(예: "전자제품")로의 변환은 프론트엔드가 담당한다.
 */
public enum AuctionCategory {
    ELECTRONICS,    // 전자제품
    FASHION,        // 패션/의류
    HOME_APPLIANCE, // 가전
    COLLECTIBLE,    // 수집품
    SPORTS,         // 스포츠
    ETC             // 기타
}
