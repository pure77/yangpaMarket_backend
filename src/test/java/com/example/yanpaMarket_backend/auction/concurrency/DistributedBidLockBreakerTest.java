package com.example.yanpaMarket_backend.auction.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.yanpaMarket_backend.config.RedissonConfig;
import com.example.yanpaMarket_backend.global.error.ApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

/**
 * [무엇] DistributedBidLock의 차단기 동작을 가짜 Redisson(Mockito)으로 검증한다. Docker 불필요.
 *   무엇을 실패/느림으로 세는지(tryLock은 예외만, unlock은 시간까지)와 열림 → 반열림 → 닫힘 흐름을 확인한다.
 */
class DistributedBidLockBreakerTest {

    private static final Duration WAIT = Duration.ofMillis(500);
    private static final Duration LEASE = Duration.ofSeconds(3);
    private static final long AUCTION = 1L;

    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);

    private CircuitBreaker breaker(Duration slowCall, Duration openWait) {
        return CircuitBreaker.of("test", RedissonConfig.breakerConfig(slowCall, openWait));
    }

    private DistributedBidLock lockWith(CircuitBreaker breaker) {
        when(redisson.getLock(Mockito.anyString())).thenReturn(lock);
        return new DistributedBidLock(new InMemoryBidLock(), redisson, WAIT, LEASE, breaker);
    }

    @Test
    void tryLock이_계속_실패하면_차단기가_열려_Redis를_건너뛰고_작업은_정상_실행된다() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new RedisException("down"));
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofSeconds(1));
        DistributedBidLock bidLock = lockWith(breaker);

        for (int i = 0; i < 15; i++) {
            assertThat(bidLock.executeWithLock(AUCTION, () -> "ok")).isEqualTo("ok");
        }

        verify(lock, atMost(10)).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(bidLock.stats().breakerSkips()).isGreaterThanOrEqualTo(5);
        assertThat(bidLock.stats().fallbacks()).isLessThanOrEqualTo(10);
    }

    @Test
    void unlock이_느리면_차단기가_열리고_다음_호출은_tryLock을_부르지_않는다() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        Mockito.doAnswer(inv -> {
            Thread.sleep(50);
            return null;
        }).when(lock).unlock();
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofSeconds(1));
        DistributedBidLock bidLock = lockWith(breaker);

        for (int i = 0; i < 10; i++) {
            bidLock.executeWithLock(AUCTION, () -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Mockito.clearInvocations(lock);
        assertThat(bidLock.executeWithLock(AUCTION, () -> "ok")).isEqualTo("ok");
        verify(lock, never()).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        assertThat(bidLock.stats().breakerSkips()).isEqualTo(1);
    }

    @Test
    void 경쟁으로_대기_초과가_나도_차단기는_닫혀_있다() throws Exception {
        // 대기 초과(false)가 slowCall(30ms)보다 오래 걸려도 느림으로 세지 않는다는 것까지 확인한다
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(inv -> {
            Thread.sleep(50);
            return false;
        });
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofSeconds(1));
        DistributedBidLock bidLock = lockWith(breaker);

        for (int i = 0; i < 30; i++) {
            assertThatThrownBy(() -> bidLock.executeWithLock(AUCTION, () -> "ok")).isInstanceOf(ApiException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(bidLock.stats().waitTimeouts()).isEqualTo(30);
        assertThat(bidLock.stats().breakerSkips()).isZero();
    }

    @Test
    void 열린_뒤_대기_시간이_지나면_반열림이_되고_정상_3건_뒤_닫힌다() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new RedisException("down"));
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofMillis(100));
        DistributedBidLock bidLock = lockWith(breaker);
        for (int i = 0; i < 10; i++) {
            bidLock.executeWithLock(AUCTION, () -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(200);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        // 열림 시간이 지나면 다음 호출이 반열림으로 넘어가며 시험 호출이 된다
        bidLock.executeWithLock(AUCTION, () -> "ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        bidLock.executeWithLock(AUCTION, () -> "ok");
        bidLock.executeWithLock(AUCTION, () -> "ok");

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 예상_밖_예외는_차단기에_실패로_기록되고_그대로_올라가며_작업은_실행되지_않는다() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new IllegalStateException("boom"));
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofSeconds(1));
        DistributedBidLock bidLock = lockWith(breaker);
        boolean[] ran = {false};

        assertThatThrownBy(() -> bidLock.executeWithLock(AUCTION, () -> {
            ran[0] = true;
            return "ok";
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ran[0]).isFalse();
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void 반열림에서_예상_밖_예외가_나도_반열림에_갇히지_않는다() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new RedisException("down"));
        CircuitBreaker breaker = breaker(Duration.ofMillis(30), Duration.ofMillis(100));
        DistributedBidLock bidLock = lockWith(breaker);
        for (int i = 0; i < 10; i++) {
            bidLock.executeWithLock(AUCTION, () -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        Thread.sleep(200);

        // 반열림 허가증 3개: 첫 호출은 예상 밖 예외, 나머지 둘은 정상. 예전에는 첫 허가증이 새서 3개가 다 채워지지 않아 갇혔다.
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(true);
        assertThatThrownBy(() -> bidLock.executeWithLock(AUCTION, () -> "ok")).isInstanceOf(IllegalStateException.class);
        bidLock.executeWithLock(AUCTION, () -> "ok");
        bidLock.executeWithLock(AUCTION, () -> "ok");

        // 허가증 3개가 모두 보고됐으므로 반열림이 판정을 끝냈다(실패율 33%라 닫힘). 결정적 단언: 더는 HALF_OPEN이 아니다.
        assertThat(breaker.getState()).isNotEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void 반열림_최대_대기는_5초로_설정된다() {
        assertThat(RedissonConfig.breakerConfig(Duration.ofMillis(30), Duration.ofSeconds(1))
                .getMaxWaitDurationInHalfOpenState()).isEqualTo(Duration.ofSeconds(5));
    }
}
