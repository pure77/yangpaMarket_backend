package com.example.yanpaMarket_backend.auction.concurrency;

import java.util.function.Supplier;

/**
 * 경매별 임계구역 보장 추상화.
 * 같은 auctionId에 대한 action은 직렬화된다. action에서 던진 예외는 그대로 전파된다.
 * 구현체: InMemoryBidLock(같은 JVM 내 직렬화), DistributedBidLock(서버 간 직렬화, Redisson 2단 락).
 * app.bid.lock.distributed.enabled=true이면 DistributedBidLock이 @Primary로 선택되고,
 * false이면 InMemoryBidLock만 쓴다.
 */
public interface BidLock {

    <T> T executeWithLock(Long auctionId, Supplier<T> action);
}
