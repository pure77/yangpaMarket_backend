package com.example.yanpaMarket_backend.auction.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import java.time.Duration;

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

    // [대기 상한] 다른 스레드가 같은 경매 락을 쥐고 있으면 상한(100ms)까지만 기다리다 409로 끊는다.
    @Test
    void 대기_상한을_넘기면_ALREADY_BIDDING으로_끊고_횟수를_센다() throws Exception {
        InMemoryBidLock lock = new InMemoryBidLock(Duration.ofMillis(100));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        holder.submit(() -> lock.executeWithLock(7L, () -> {
            held.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertThat(held.await(2, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        Throwable thrown = catchThrowable(() -> lock.executeWithLock(7L, () -> "never"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        release.countDown();
        holder.shutdown();

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getErrorCode()).isEqualTo(ErrorCode.ALREADY_BIDDING);
        assertThat(elapsedMs).isBetween(100L, 1000L);
        assertThat(lock.waitTimeouts()).isEqualTo(1);
    }

    @Test
    void 락이_비어_있으면_즉시_통과하고_타임아웃_횟수는_0이다() {
        InMemoryBidLock lock = new InMemoryBidLock(Duration.ofMillis(100));
        assertThat(lock.executeWithLock(3L, () -> "ok")).isEqualTo("ok");
        assertThat(lock.waitTimeouts()).isZero();
    }

    @Test
    void 같은_스레드의_중첩_호출은_재진입된다() {
        InMemoryBidLock lock = new InMemoryBidLock(Duration.ofMillis(100));
        String r = lock.executeWithLock(5L, () -> lock.executeWithLock(5L, () -> "inner"));
        assertThat(r).isEqualTo("inner");
        assertThat(lock.waitTimeouts()).isZero();
    }
}
