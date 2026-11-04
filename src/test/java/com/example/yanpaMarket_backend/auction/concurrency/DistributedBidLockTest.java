package com.example.yanpaMarket_backend.auction.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.config.RedissonConfig;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;

/**
 * [무엇] DistributedBidLock을 "서버 2대"처럼 검증한다: 서버마다 자기 InMemoryBidLock과 자기 RedissonClient를 갖는
 *   인스턴스 두 개를 만들어 같은 Redis 컨테이너를 공유하게 한다(MultiServerBidTest보다 훨씬 가볍고 빠르다).
 */
class DistributedBidLockTest extends RedisTestSupport {

    private static final Duration WAIT = Duration.ofMillis(500);
    private static final Duration LEASE = Duration.ofSeconds(3);

    private final List<RedissonClient> clients = new ArrayList<>();

    private DistributedBidLock server(Duration wait, Duration lease) {
        RedissonClient client = Redisson.create(RedissonConfig.config(redisHost(), redisPort(), "", 0,
                Duration.ofMillis(500), Duration.ofMillis(500)));
        clients.add(client);
        return new DistributedBidLock(new InMemoryBidLock(), client, wait, lease);
    }

    @AfterEach
    void shutdownClients() {
        clients.forEach(RedissonClient::shutdown);
    }

    @Test
    void 서버_두_대가_같은_경매를_동시에_처리하지_않는다() throws Exception {
        DistributedBidLock a = server(WAIT, LEASE);
        DistributedBidLock b = server(WAIT, LEASE);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        int[] counter = {0}; // 비원자적 증가 — 두 서버가 겹치면 lost update
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            DistributedBidLock target = t % 2 == 0 ? a : b;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    target.executeWithLock(7L, () -> {
                        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        int read = counter[0];
                        Thread.onSpinWait();
                        counter[0] = read + 1;
                        inFlight.decrementAndGet();
                        return null;
                    });
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(maxInFlight.get()).as("동시에 임계구역 안에 있던 최대 수").isEqualTo(1);
        assertThat(counter[0]).isEqualTo(16 * perThread);
        assertThat(a.stats().fallbacks() + b.stats().fallbacks()).as("fail-open 없이 전부 Redis 락").isZero();
    }

    @Test
    void 결과를_돌려주고_예외는_그대로_던지며_락은_풀린다() {
        DistributedBidLock a = server(WAIT, LEASE);
        DistributedBidLock b = server(WAIT, LEASE);

        assertThat(a.executeWithLock(8L, () -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> a.executeWithLock(8L, () -> {
            throw new IllegalStateException("도메인 거절");
        })).isInstanceOf(IllegalStateException.class);

        long start = System.nanoTime();
        assertThat(b.executeWithLock(8L, () -> "b")).isEqualTo("b");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("풀린 락은 기다리지 않는다")
                .isLessThan(Duration.ofMillis(300));
        assertThat(sharedRedis().hasKey(BidRedisKeys.lock(8L))).as("해제 후 키 없음").isFalse();
    }

    @Test
    void 다른_서버가_오래_잡고_있으면_대기_시간_뒤_ALREADY_BIDDING() throws Exception {
        DistributedBidLock a = server(WAIT, LEASE);
        DistributedBidLock b = server(Duration.ofMillis(200), LEASE);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> holder = pool.submit(() -> a.executeWithLock(9L, () -> {
            held.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        held.await(5, TimeUnit.SECONDS);

        long start = System.nanoTime();
        assertThatThrownBy(() -> b.executeWithLock(9L, () -> null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_BIDDING);
        Duration waited = Duration.ofNanos(System.nanoTime() - start);
        assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(180)).isLessThan(Duration.ofSeconds(1));
        assertThat(b.stats().waitTimeouts()).isEqualTo(1);

        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @Test
    void 잡은_락에는_만료_시간이_걸려_있다() throws Exception {
        DistributedBidLock a = server(WAIT, Duration.ofSeconds(3));
        long ttl = a.executeWithLock(10L, () -> sharedRedis().getExpire(BidRedisKeys.lock(10L), TimeUnit.MILLISECONDS));

        assertThat(ttl).as("감시견이 아니라 명시한 lease").isBetween(1L, 3_000L);
    }
}
