package com.example.yanpaMarket_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.concurrency.BidLock;
import com.example.yanpaMarket_backend.auction.concurrency.DistributedBidLock;
import com.example.yanpaMarket_backend.auction.concurrency.InMemoryBidLock;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * [무엇] 분산 락 스위치(app.bid.lock.distributed.enabled)가 BidService에 주입되는 BidLock을 바꾸는지 확인한다.
 * [어떻게] 켬 → @Primary DistributedBidLock, 끔(테스트 기본) → 기존 InMemoryBidLock 그대로.
 */
class DistributedLockWiringTest {

    @Nested
    @SpringBootTest(properties = "app.bid.lock.distributed.enabled=true")
    @ActiveProfiles("test")
    class 켬 extends RedisTestSupport {
        @Autowired BidLock bidLock;

        @Test
        void BidService가_받는_락은_분산_락이다() {
            assertThat(bidLock).isInstanceOf(DistributedBidLock.class);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class 끔 {
        @Autowired BidLock bidLock;

        @Test
        void 테스트_기본값은_메모리_락_그대로다() {
            assertThat(bidLock).isInstanceOf(InMemoryBidLock.class);
        }
    }
}
