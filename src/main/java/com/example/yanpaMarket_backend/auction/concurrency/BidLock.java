package com.example.yanpaMarket_backend.auction.concurrency;

import java.util.function.Supplier;

/**
 * 경매별 임계구역 보장 추상화.
 * 같은 auctionId에 대한 action은 직렬화된다. action에서 던진 예외는 그대로 전파된다.
 * (후속: 멀티 인스턴스 전환 시 RedisBidLock 구현체로 교체)
 */
public interface BidLock {

    <T> T executeWithLock(Long auctionId, Supplier<T> action);
}
