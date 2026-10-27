package com.example.yanpaMarket_backend.auction.precheck;

import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [무엇] 사전 거절을 끈 구현. 항상 통과, 기록 안 함 → 동작이 Redis 도입 전과 똑같다.
 * [언제] app.bid.precheck.enabled=false — Redis 문제 시 비상 스위치이자, 부하 테스트 A/B의 기준선.
 */
@Component
@ConditionalOnProperty(name = "app.bid.precheck.enabled", havingValue = "false")
public class NoOpBidPriceGuard implements BidPriceGuard {

    @Override
    public PrecheckResult precheck(Long auctionId, Long bidderUserId, long amount, LocalDateTime now) {
        return PrecheckResult.PASS;
    }

    @Override
    public void record(Long auctionId, BidPriceSnapshot snapshot) {
    }

    @Override
    public void evict(Long auctionId) {
    }
}
