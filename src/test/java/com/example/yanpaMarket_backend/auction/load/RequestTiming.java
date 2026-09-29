package com.example.yanpaMarket_backend.auction.load;

/**
 * [무엇] 요청 하나 안에서 필터와 측정 데코레이터들이 시간을 주고받는 통로(ThreadLocal).
 *
 * [왜 필요한가 — 백분위 뺄셈을 없애기 위해]
 *   예전에는 구간을 이렇게 추정했다.
 *     (c) 트랜잭션 = p95(락 보유) - p95(broadcast)
 *     (a) 진입·조회 = p95(응답)   - p95(락 대기) - p95(락 보유)
 *   그런데 p95(A+B) != p95(A) + p95(B) 다. 요청마다 느린 구간이 다르기 때문에
 *   백분위끼리 빼면 실제로 존재하지 않는 값이 나온다.
 *
 *   지금은 한 요청이 끝나는 시점에 "그 요청의" 각 구간을 모아 한 번에 기록한다.
 *   입찰은 전부 HTTP POST라 모든 락 획득이 요청 스레드(Tomcat worker) 위에서 일어나므로
 *   ThreadLocal 하나로 정확히 묶인다.
 *
 * [슬롯 구성] [0]=락 대기, [1]=락 보유, [2]=broadcast
 *   - TimingBidLock 이 [0], [1] 을 채운다
 *   - SimpMessagingTemplate spy 가 [2] 를 채운다(락 보유 안에서 실행되므로 같은 스레드)
 *   - ServerTimingFilter 가 요청 종료 시 읽어 LoadSamples 에 기록하고 비운다
 */
final class RequestTiming {

    static final ThreadLocal<long[]> SLOT = ThreadLocal.withInitial(() -> new long[3]);

    static final int WAIT = 0;
    static final int HOLD = 1;
    static final int BROADCAST = 2;

    private RequestTiming() {
    }

    static void reset() {
        long[] slot = SLOT.get();
        slot[WAIT] = 0;
        slot[HOLD] = 0;
        slot[BROADCAST] = 0;
    }
}
