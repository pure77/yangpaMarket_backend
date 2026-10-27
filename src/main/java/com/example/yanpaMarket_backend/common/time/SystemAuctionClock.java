package com.example.yanpaMarket_backend.common.time;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** [무엇] 기본 구현 — 이 서버의 시스템 시계. 기존 LocalDateTime.now()와 같은 값이다. */
@Component
public class SystemAuctionClock implements AuctionClock {

    private final Clock clock = Clock.systemDefaultZone();

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
