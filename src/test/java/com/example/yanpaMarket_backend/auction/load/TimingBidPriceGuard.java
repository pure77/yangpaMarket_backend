package com.example.yanpaMarket_backend.auction.load;

import com.example.yanpaMarket_backend.auction.precheck.BidPriceGuard;
import com.example.yanpaMarket_backend.auction.precheck.BidPriceSnapshot;
import com.example.yanpaMarket_backend.auction.precheck.PrecheckResult;
import java.time.LocalDateTime;

/**
 * [무엇] 사전 거절 시간을 재는 측정용 데코레이터(TimingBidLock과 같은 방식).
 *   실제 판정은 위임받은 구현(Redis 또는 NoOp)이 그대로 한다.
 */
final class TimingBidPriceGuard implements BidPriceGuard {

    private final BidPriceGuard delegate;

    TimingBidPriceGuard(BidPriceGuard delegate) {
        this.delegate = delegate;
    }

    @Override
    public PrecheckResult precheck(Long auctionId, Long bidderUserId, long amount, LocalDateTime now) {
        long start = System.nanoTime();
        try {
            return delegate.precheck(auctionId, bidderUserId, amount, now);
        } finally {
            RequestTiming.SLOT.get()[RequestTiming.PRECHECK] = System.nanoTime() - start;
        }
    }

    @Override
    public void record(Long auctionId, BidPriceSnapshot snapshot) {
        delegate.record(auctionId, snapshot);
    }

    @Override
    public void evict(Long auctionId) {
        delegate.evict(auctionId);
    }
}
