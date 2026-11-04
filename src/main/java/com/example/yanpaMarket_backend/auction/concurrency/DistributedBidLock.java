package com.example.yanpaMarket_backend.auction.concurrency;

import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.precheck.GuardFailureLog;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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
 */
public class DistributedBidLock implements BidLock {

    private final InMemoryBidLock local;
    private final RedissonClient redisson;
    private final long waitMillis;
    private final long leaseMillis;
    private final GuardFailureLog failureLog =
            new GuardFailureLog("Redis 분산 락", "서버 메모리 락만으로 처리합니다(@Version이 정합성 보장)");

    private final AtomicLong acquired = new AtomicLong();
    private final AtomicLong waitTimeouts = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong releaseFailures = new AtomicLong();

    public DistributedBidLock(InMemoryBidLock local, RedissonClient redisson, Duration wait, Duration lease) {
        this.local = local;
        this.redisson = redisson;
        this.waitMillis = wait.toMillis();
        this.leaseMillis = lease.toMillis();
    }

    @Override
    public <T> T executeWithLock(Long auctionId, Supplier<T> action) {
        return local.executeWithLock(auctionId, () -> {
            RLock lock = redisson.getLock(BidRedisKeys.lock(auctionId));
            boolean held;
            try {
                held = lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.ALREADY_BIDDING);
            } catch (RedisException e) {
                fallbacks.incrementAndGet();
                failureLog.failure("lock", e);
                return action.get(); // fail-open: 1단(메모리 락) 안에서 그대로 실행
            }
            if (!held) {
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
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException | RedisException e) {
            // IllegalMonitorStateException: lease가 먼저 만료돼 이미 내 락이 아님 / RedisException: Redis 끊김
            releaseFailures.incrementAndGet();
            failureLog.failure("unlock", e);
        }
    }

    public LockStats stats() {
        return new LockStats(acquired.get(), waitTimeouts.get(), fallbacks.get(), releaseFailures.get());
    }
}
