package com.example.yanpaMarket_backend.auction.precheck;

import java.time.Duration;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * [무엇] Redis 실패 로그의 폭주를 막는다.
 *   R1) WARN은 장애가 들쭉날쭉(성공↔실패 반복)해도 interval(10초)에 최대 한 번이다.
 *       - 맨 처음 실패는 바로 WARN + 스택트레이스.
 *       - 새 장애가 시작돼도 마지막 WARN으로부터 interval이 지나지 않았으면 남기지 않고 센다.
 *       - 장애가 이어지는 동안은 interval마다 요약 WARN 한 번.
 *   R2) 복구 INFO는 그 장애 구간에서 WARN이 한 번이라도 남았을 때만 남긴다(INFO 줄 수 ≤ WARN 줄 수).
 *       상태(실패 중 → 정상)는 로그 여부와 무관하게 매번 되돌린다.
 *
 * [왜] Redis가 죽은 동안 초당 100건이 들어오면, 실패마다 WARN을 남길 경우 초당 100줄이 쌓여
 *   정작 다른 로그를 묻고 디스크 I/O까지 늘린다. 요약의 "N건"은 근사치이며,
 *   정확한 실패 건수는 카운터(GuardStats)가 센다.
 *
 * [동시성] 상태(시각·플래그·건수)를 짧은 synchronized 구간 하나로 묶어, 시각을 읽기 전에
 *   다른 스레드가 먼저 상태를 바꾸는 경쟁이 없다. 정상 경로의 recovered()는 volatile 읽기 한 번이다.
 *
 * [nanoClock 주입] 테스트에서 10초를 기다리지 않고 시각을 넘기기 위해서다.
 */
@Slf4j
final class GuardFailureLog {

    private final long intervalNanos;
    private final long intervalSeconds;
    private final LongSupplier nanoClock;

    // 정상 경로에서 락 없이 읽기 위해 volatile. 쓰기는 항상 synchronized(this) 안에서만 한다.
    private volatile boolean failing;
    private boolean everWarned;       // WARN을 한 번이라도 남겼나(첫 실패 즉시 로그 판단)
    private boolean warnedInOutage;   // 지금 장애 구간에서 WARN을 남겼나(복구 INFO 판단)
    private long lastWarnAt;          // 마지막 WARN 시각(nano)
    private long suppressed;          // 마지막 WARN 이후 남기지 않은 실패 수(근사)

    GuardFailureLog() {
        this(Duration.ofSeconds(10), System::nanoTime);
    }

    GuardFailureLog(Duration interval, LongSupplier nanoClock) {
        this.intervalNanos = interval.toNanos();
        this.intervalSeconds = interval.toSeconds();
        this.nanoClock = nanoClock;
    }

    /** @return 이번 호출에서 로그를 남겼으면 true */
    synchronized boolean failure(String operation, RuntimeException e) {
        long now = nanoClock.getAsLong();
        boolean due = !everWarned || now - lastWarnAt >= intervalNanos;

        if (!failing) {
            // 새 장애 시작. interval이 안 지났으면(들쭉날쭉) 남기지 않고 센다.
            failing = true;
            warnedInOutage = false;
            if (due) {
                log.warn("Redis bid guard 실패 — 사전 거절 없이 기존 락 경로로 처리합니다 (op={})", operation, e);
                markWarned(now);
                return true;
            }
            suppressed++;
            return false;
        }

        // 장애가 이어지는 중: interval마다 요약 한 번.
        if (due) {
            log.warn("Redis bid guard 실패 약 {}건 (최근 {}s, 마지막 op={}: {})",
                    suppressed + 1, intervalSeconds, operation, e.toString());
            markWarned(now);
            return true;
        }
        suppressed++;
        return false;
    }

    /** @return 실패 상태에서 막 벗어나 INFO를 남겼으면 true. 정상 경로에서 매번 불려도 비용은 volatile 읽기 한 번이다. */
    boolean recovered() {
        if (!failing) {
            return false;
        }
        synchronized (this) {
            if (!failing) {
                return false;
            }
            failing = false;
            boolean shouldLog = warnedInOutage; // WARN 없이 끝난 장애는 복구 INFO도 없다
            warnedInOutage = false;
            if (shouldLog) {
                log.info("Redis bid guard 복구 — 사전 거절을 다시 사용합니다");
            }
            return shouldLog;
        }
    }

    private void markWarned(long now) {
        everWarned = true;
        warnedInOutage = true;
        lastWarnAt = now;
        suppressed = 0;
    }
}
