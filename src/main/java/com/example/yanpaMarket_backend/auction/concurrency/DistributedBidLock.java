package com.example.yanpaMarket_backend.auction.concurrency;

import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.precheck.GuardFailureLog;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import com.example.yanpaMarket_backend.config.RedissonConfig;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

/**
 * [무엇] 서버 여러 대에서 같은 경매의 입찰을 한 줄로 처리하는 2단 락.
 *   1단: InMemoryBidLock — 같은 서버로 들어온 요청은 서버 메모리 안에서 줄을 선다(Redis 요청 0).
 *   2단: Redisson RLock("auction:{id}:lock") — 각 서버의 줄 맨 앞 1건만 서버끼리 다툰다.
 *   → Redis 락 경쟁이 입찰자 수가 아니라 서버 수에 비례한다(서버 2대면 동시에 기다리는 요청 최대 1건).
 *
 * [잡을 때 안쪽부터, 풀 때 바깥쪽부터] 메모리 락 → Redis 락 → 작업 → Redis 해제 → 메모리 해제.
 *
 * [대기 초과 → 409] wait 안에 못 잡으면 ALREADY_BIDDING. 기존 409와 같은 코드라 프론트 계약이 그대로다.
 * [lease 명시] 감시견(자동 연장, 기본 30초)을 쓰지 않는다. 락 잡은 서버가 죽어도 lease 뒤 풀린다.
 *   작업이 lease보다 길어져 락이 먼저 풀리면 다른 서버가 들어올 수 있지만, Auction의 @Version이 막는다(409).
 *
 * [fail-open] Redis가 죽거나 끊기거나 느리면(RedisException) 2단을 건너뛰고 1단만으로 처리한다.
 *   = 분산 락 도입 전 서버 2대 상태. 데이터는 @Version이 지키고 409만 다시 생긴다. 사전 거절과 같은 원칙
 *   ("Redis가 없으면 느려지거나 409가 늘 뿐, 틀리지 않는다"). 로그는 GuardFailureLog로 10초에 한 줄.
 *
 * [차단기] Redis가 "죽지 않고 느릴" 때, 입찰마다 Redis 타임아웃을 메모리 락(스트라이프)을 쥔 채 기다리면 인기 경매에
 *   요청 스레드가 쌓인다. 그래서 Resilience4j 차단기가 Redis 호출 결과를 지켜보다가 열리면 2단을 통째로 건너뛴다(= fail-open).
 *   닫힘(정상: Redis 사용) → 열림(최근 20건 중 절반 이상이 실패/느림이면 open-wait 동안 Redis를 아예 안 부름, breakerSkips로 집계)
 *   → 반열림(open-wait 뒤 3건만 시험해 정상이면 닫힘, 아니면 다시 열림).
 *   무엇을 세나: tryLock은 "다른 서버가 쥔 락을 기다리는 것"이 정상이라 걸린 시간은 세지 않고 예외(RedisException)만 실패로 센다.
 *   대기 초과(false)는 Redis가 응답한 것이므로 성공으로 기록한다(경쟁이 심해도 차단기가 열리면 안 된다).
 *   unlock은 기다림 없는 왕복 1회라 걸린 시간까지 센다(느림 판정). lease 만료(IllegalMonitorStateException)도 Redis 잘못이 아니라 성공.
 *   한 번 얻은 permission은 tryLock 예외·대기 초과·unlock 결과 중 정확히 한 번만 기록된다(InterruptedException만 반납).
 *   아래 알려진 한계 1·2는 차단기가 열리면 open-wait(1초) 단위로 줄어든다.
 *
 * [알려진 한계 — 정직하게]
 *   1) 메모리 스트라이프(1단)는 Redis 락을 기다리는 동안(최대 wait) 계속 잡고 있다. 그래서 같은 서버에서
 *      같은 스트라이프를 공유하는 다른 경매의 입찰도 그만큼 더 기다릴 수 있다.
 *   2) 느린 Redis 때문에 tryLock이 시간 초과로 끝났는데 Redis에는 이미 락이 걸려 있었다면, fail-open으로
 *      작업은 실행되지만 그 키는 lease가 끝날 때까지 남는다. 그 경매는 최대 lease(3초) 동안 409가 날 수 있다.
 *      데이터는 @Version이 지키므로 정합성은 안전하다.
 */
public class DistributedBidLock implements BidLock {

    private final InMemoryBidLock local;
    private final RedissonClient redisson;
    private final CircuitBreaker breaker;
    private final long waitMillis;
    private final long leaseMillis;
    private final GuardFailureLog failureLog =
            new GuardFailureLog("Redis 분산 락", "서버 메모리 락만으로 처리합니다(@Version이 정합성 보장)");

    private final AtomicLong acquired = new AtomicLong();
    private final AtomicLong waitTimeouts = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong breakerSkips = new AtomicLong();
    private final AtomicLong releaseFailures = new AtomicLong();

    /** 기본 차단기(느림 30ms, 열림 1s)로 만든다. 기존 테스트용. */
    public DistributedBidLock(InMemoryBidLock local, RedissonClient redisson, Duration wait, Duration lease) {
        this(local, redisson, wait, lease, CircuitBreaker.of("redisBidLock",
                RedissonConfig.breakerConfig(Duration.ofMillis(30), Duration.ofSeconds(1))));
    }

    public DistributedBidLock(InMemoryBidLock local, RedissonClient redisson, Duration wait, Duration lease,
                              CircuitBreaker breaker) {
        this.breaker = breaker;
        this.local = local;
        this.redisson = redisson;
        this.waitMillis = wait.toMillis();
        this.leaseMillis = lease.toMillis();
    }

    @Override
    public <T> T executeWithLock(Long auctionId, Supplier<T> action) {
        return local.executeWithLock(auctionId, () -> {
            if (!breaker.tryAcquirePermission()) { // 열림: Redis를 기다리지 않는다
                breakerSkips.incrementAndGet();
                return action.get();
            }
            RLock lock = redisson.getLock(BidRedisKeys.lock(auctionId));
            long start = System.nanoTime();
            boolean held;
            try {
                held = lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                breaker.releasePermission();
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.ALREADY_BIDDING);
            } catch (RedisException e) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                fallbacks.incrementAndGet();
                failureLog.failure("lock", e);
                return action.get(); // fail-open: 1단(메모리 락) 안에서 그대로 실행
            }
            if (!held) {
                breaker.onSuccess(0, TimeUnit.NANOSECONDS); // Redis는 응답했다. 경쟁으로 못 잡았을 뿐
                waitTimeouts.incrementAndGet();
                throw new ApiException(ErrorCode.ALREADY_BIDDING);
            }
            failureLog.recovered();
            acquired.incrementAndGet();
            try {
                return action.get();
            } finally {
                release(lock);
            }
        });
    }

    /** 풀기 실패는 삼킨다 — 작업(커밋)은 이미 끝났고, 락은 lease 뒤 스스로 풀린다. 실패 응답을 내면 안 된다. */
    private void release(RLock lock) {
        long start = System.nanoTime();
        try {
            lock.unlock();
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (IllegalMonitorStateException e) {
            // lease가 먼저 만료돼 이미 내 락이 아님 — Redis는 정상 응답했으므로 성공으로 기록
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            releaseFailures.incrementAndGet();
            failureLog.failure("unlock", e);
        } catch (RedisException e) {
            // Redis 끊김
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            releaseFailures.incrementAndGet();
            failureLog.failure("unlock", e);
        }
    }

    public LockStats stats() {
        return new LockStats(acquired.get(), waitTimeouts.get(), fallbacks.get(), breakerSkips.get(), releaseFailures.get());
    }
}
