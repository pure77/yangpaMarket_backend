package com.example.yanpaMarket_backend.auction.concurrency;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 고정 개수(256) ReentrantLock 스트라이프 기반 인메모리 락.
 * auctionId 해시로 스트라이프를 선택하므로 락 객체 수가 무한 증가하지 않는다.
 * 단일 인스턴스 한정 — 서버 2대 이상에서는 무력화되며, 그 시점이 Redis 분산 락 도입 트리거다.
 */
@Component
public class InMemoryBidLock implements BidLock {

    private static final int STRIPES = 256;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public InMemoryBidLock() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public <T> T executeWithLock(Long auctionId, Supplier<T> action) {
        ReentrantLock lock = locks[Math.floorMod(auctionId.hashCode(), STRIPES)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
