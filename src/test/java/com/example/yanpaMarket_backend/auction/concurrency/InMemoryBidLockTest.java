package com.example.yanpaMarket_backend.auction.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryBidLockTest {

    @Test
    void 같은_경매키에_대한_동시_실행을_직렬화한다() throws InterruptedException {
        BidLock lock = new InMemoryBidLock();
        int[] counter = {0}; // 비원자적 증가 — 락이 없으면 lost update 발생
        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                lock.executeWithLock(42L, () -> {
                    counter[0] = counter[0] + 1;
                    return null;
                });
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(counter[0]).isEqualTo(threads);
    }

    @Test
    void 반환값을_그대로_전달한다() {
        BidLock lock = new InMemoryBidLock();
        String result = lock.executeWithLock(1L, () -> "ok");
        assertThat(result).isEqualTo("ok");
    }
}
