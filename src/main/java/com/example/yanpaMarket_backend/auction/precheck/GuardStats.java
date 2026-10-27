package com.example.yanpaMarket_backend.auction.precheck;

/** [무엇] RedisBidPriceGuard 카운터의 스냅샷. 부하 테스트는 구간 전후 차이(minus)로 읽는다. */
public record GuardStats(long precheckPass, long precheckTooLow, long precheckSelfBid,
                         long precheckFailures, long recordFailures, long evictFailures) {

    public GuardStats minus(GuardStats before) {
        return new GuardStats(
                precheckPass - before.precheckPass,
                precheckTooLow - before.precheckTooLow,
                precheckSelfBid - before.precheckSelfBid,
                precheckFailures - before.precheckFailures,
                recordFailures - before.recordFailures,
                evictFailures - before.evictFailures);
    }
}
