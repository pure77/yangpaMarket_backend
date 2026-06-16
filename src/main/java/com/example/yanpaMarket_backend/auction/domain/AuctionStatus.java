package com.example.yanpaMarket_backend.auction.domain; // auction.domain = 경매 도메인 패키지

/**
 * [무엇] 경매의 "진행 상태"를 나타내는 enum (DB 스키마 status ENUM과 동일).
 * [연결]
 *   - Auction.status 컬럼에 문자열로 저장된다.
 *   - 현재 구현 범위에서는 ACTIVE/CANCELLED 만 능동적으로 전이된다(나머지는 결제/낙찰 흐름에서 확장 예정).
 */
public enum AuctionStatus {
    ACTIVE,          // 진행 중(입찰 가능)
    PAYMENT_PENDING, // 낙찰 후 결제 대기
    PAID,            // 결제 완료
    ENDED,           // 종료
    CANCELLED        // 취소됨
}
