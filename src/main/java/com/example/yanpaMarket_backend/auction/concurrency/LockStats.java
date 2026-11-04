package com.example.yanpaMarket_backend.auction.concurrency;

/**
 * [무엇] DistributedBidLock 카운터 스냅샷. 측정 리포트(MultiServerLoadTest)가 읽는다.
 *   acquired: Redis 락을 잡고 처리 / waitTimeouts: 대기 초과로 409 / fallbacks: Redis 실패로 메모리 락만으로 처리 / breakerSkips: 차단기가 열려 Redis를 건너뛰고 처리 /
 *   releaseFailures: 풀기 실패(lease 만료·Redis 끊김 — 락은 lease 뒤 스스로 풀린다)
 */
public record LockStats(long acquired, long waitTimeouts, long fallbacks, long breakerSkips, long releaseFailures) {
}
