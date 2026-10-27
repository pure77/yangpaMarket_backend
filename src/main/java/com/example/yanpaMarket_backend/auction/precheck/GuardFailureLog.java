package com.example.yanpaMarket_backend.auction.precheck;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * [무엇] Redis 실패 로그의 폭주를 막는다.
 *   첫 실패: WARN + 스택트레이스 / 이후: interval(10초)에 한 번 요약 WARN / 실패 후 첫 성공: INFO 1회.
 *
 * [왜] Redis가 죽은 동안 초당 100건이 들어오면, 실패마다 WARN을 남길 경우 초당 100줄이 쌓여
 *   정작 다른 로그를 묻고 디스크 I/O까지 늘린다. 실패 "건수"는 카운터(GuardStats)가 정확히 센다.
 *
 * [nanoClock 주입] 테스트에서 10초를 기다리지 않고 시각을 넘기기 위해서다.
 */
@Slf4j
final class GuardFailureLog {

    private final long intervalNanos;
    private final long intervalSeconds;
    private final LongSupplier nanoClock;
    private final AtomicBoolean failing = new AtomicBoolean();
    private final AtomicLong lastLoggedAt = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();

    GuardFailureLog() {
        this(Duration.ofSeconds(10), System::nanoTime);
    }

    GuardFailureLog(Duration interval, LongSupplier nanoClock) {
        this.intervalNanos = interval.toNanos();
        this.intervalSeconds = interval.toSeconds();
        this.nanoClock = nanoClock;
    }

    /** @return 이번 호출에서 로그를 남겼으면 true */
    boolean failure(String operation, RuntimeException e) {
        long now = nanoClock.getAsLong();
        if (failing.compareAndSet(false, true)) {
            lastLoggedAt.set(now);
            log.warn("Redis bid guard 실패 — 사전 거절 없이 기존 락 경로로 처리합니다 (op={})", operation, e);
            return true;
        }
        long last = lastLoggedAt.get();
        if (now - last >= intervalNanos && lastLoggedAt.compareAndSet(last, now)) {
            log.warn("Redis bid guard 실패 {}건 (최근 {}s, 마지막 op={}: {})",
                    suppressed.getAndSet(0) + 1, intervalSeconds, operation, e.toString());
            return true;
        }
        suppressed.incrementAndGet();
        return false;
    }

    /** @return 실패 상태에서 막 벗어나 로그를 남겼으면 true. 정상 경로에서 매번 불려도 비용은 읽기 한 번이다. */
    boolean recovered() {
        if (failing.get() && failing.compareAndSet(true, false)) {
            suppressed.set(0);
            log.info("Redis bid guard 복구 — 사전 거절을 다시 사용합니다");
            return true;
        }
        return false;
    }
}
