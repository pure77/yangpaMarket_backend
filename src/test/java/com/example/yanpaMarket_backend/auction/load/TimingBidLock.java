package com.example.yanpaMarket_backend.auction.load;

import com.example.yanpaMarket_backend.auction.concurrency.BidLock;
import java.util.function.Supplier;

/**
 * [무엇] 실제 BidLock을 감싸 "락 대기 시간"과 "락 보유 시간"을 분리 측정하는 데코레이터.
 *
 * [왜 필요한가 — 부하 테스트의 핵심]
 *   응답 시간만 보면 락이 병목인지 DB가 병목인지 구분할 수 없다. 예를 들어 800ms가
 *     락 대기 750ms + 보유 50ms  → 처리량 한계. 임계구역을 줄여야 한다
 *     락 대기  50ms + 보유 750ms → DB가 느린 것. 락은 죄가 없다
 *   원인이 정반대인데 밖에서는 똑같이 800ms로 보인다. 그래서 안을 갈라 재야 한다.
 *
 * [어떻게 재나]
 *   executeWithLock 전체 시간 = 대기 + 보유
 *   전달받은 action(임계구역 본문)을 한 번 더 감싸서 그 실행 시간 = 보유
 *   대기 = 전체 - 보유
 *
 * [기록 방식] 값을 직접 LoadSamples에 넣지 않고 RequestTiming(ThreadLocal)에 놓는다.
 *   ServerTimingFilter가 요청이 끝날 때 "그 요청의" 구간들을 한꺼번에 확정한다.
 *   그래야 백분위끼리 빼는 부정확한 추정 없이 요청별로 정확한 분해가 나온다.
 *
 * [프로덕션 코드 무수정] 이 클래스는 test 소스셋에만 있고,
 *   HotAuctionLoadTest의 @TestConfiguration이 @Primary로 등록해 BidService에 주입시킨다.
 *   실제 잠금은 그대로 InMemoryBidLock에 위임하므로 동작은 조금도 달라지지 않는다.
 */
final class TimingBidLock implements BidLock {

    private final BidLock delegate;

    TimingBidLock(BidLock delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T executeWithLock(Long auctionId, Supplier<T> action) {
        long totalStart = System.nanoTime();
        // 보유 시간을 람다 안에서 기록해야 하므로 배열에 담아 밖으로 빼낸다(사실상 out 파라미터).
        long[] holdNanos = new long[1];
        try {
            return delegate.executeWithLock(auctionId, () -> {
                long holdStart = System.nanoTime();
                try {
                    return action.get();
                } finally {
                    // 규칙 위반으로 예외가 나도 락은 잡고 있었으므로 보유 시간에 포함한다.
                    holdNanos[0] = System.nanoTime() - holdStart;
                }
            });
        } finally {
            long total = System.nanoTime() - totalStart;
            long[] slot = RequestTiming.SLOT.get();
            slot[RequestTiming.HOLD] = holdNanos[0];
            slot[RequestTiming.WAIT] = Math.max(0, total - holdNanos[0]);
        }
    }
}
