package com.example.yanpaMarket_backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTimesTest {

    @Test
    void 서울_시각으로_해석해_epoch_ms로_바꾼다() {
        // 서울 09:00 == UTC 00:00
        LocalDateTime seoulNine = LocalDateTime.of(2026, 1, 1, 9, 0);
        assertThat(AuctionTimes.toEpochMillis(seoulNine))
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
    }

    @Test
    void 밀리초까지_보존하고_그_아래는_버린다() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 26, 20, 0, 0, 123_456_789);
        assertThat(AuctionTimes.toEpochMillis(t) % 1000).isEqualTo(123);
    }
}
