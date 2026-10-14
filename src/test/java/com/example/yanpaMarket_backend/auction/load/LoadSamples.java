package com.example.yanpaMarket_backend.auction.load;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [무엇] 부하 테스트에서 나노초 샘플을 모아 백분위수를 뽑는 수집기.
 *
 * [왜 직접 만드나] 부하 도구(k6/Gatling)를 쓰지 않고 JUnit 안에서 측정하기 때문에
 *   백분위수 계산이 필요하다. 외부 라이브러리를 추가할 만큼 복잡한 일은 아니다.
 *
 * [메모리 상한] 구독자 100명 x 수천 건이면 샘플이 수십만 개가 된다.
 *   MAX_SAMPLES를 넘으면 개수만 세고 값은 버린다(백분위수는 이미 충분히 안정적이다).
 */
final class LoadSamples {

    private static final int MAX_SAMPLES = 500_000;

    private final ConcurrentLinkedQueue<Long> nanos = new ConcurrentLinkedQueue<>();
    private final AtomicLong count = new AtomicLong();

    void add(long valueNanos) {
        if (count.incrementAndGet() <= MAX_SAMPLES) {
            nanos.add(valueNanos);
        }
    }

    /** 스윕 모드에서 구간(phase)마다 통계를 새로 모으기 위해 비운다. */
    void reset() {
        nanos.clear();
        count.set(0);
    }

    long count() {
        return count.get();
    }

    boolean isEmpty() {
        return nanos.isEmpty();
    }

    /** 백분위수를 밀리초로 반환한다. q=0.5 → p50. 샘플이 없으면 0. */
    double percentileMillis(double q) {
        long[] sorted = sortedNanos();
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(q * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index] / 1_000_000.0;
    }

    double meanMillis() {
        long[] sorted = sortedNanos();
        if (sorted.length == 0) {
            return 0;
        }
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        return (sum / (double) sorted.length) / 1_000_000.0;
    }

    private long[] sortedNanos() {
        long[] values = nanos.stream().mapToLong(Long::longValue).toArray();
        java.util.Arrays.sort(values);
        return values;
    }
}
