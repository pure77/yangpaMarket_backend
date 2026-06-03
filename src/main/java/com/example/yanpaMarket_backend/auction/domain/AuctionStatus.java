package com.example.yanpaMarket_backend.auction.domain;

/** 경매 상태. 스키마 status ENUM과 동일. 이번 범위는 ACTIVE/CANCELLED만 능동 전이. */
public enum AuctionStatus {
    ACTIVE,
    PAYMENT_PENDING,
    PAID,
    ENDED,
    CANCELLED
}
