package com.example.yanpaMarket_backend.auction.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GuardFailureLogTest {

    private long nowNanos = 0;
    private final GuardFailureLog log = new GuardFailureLog(Duration.ofSeconds(10), () -> nowNanos);
    private final RuntimeException error = new RuntimeException("connection refused");

    @Test
    void 첫_실패는_바로_남기고_그_뒤로는_10초에_한_번만_남긴다() {
        assertThat(log.failure("precheck", error)).isTrue();

        nowNanos += Duration.ofSeconds(1).toNanos();
        assertThat(log.failure("precheck", error)).isFalse();

        nowNanos += Duration.ofSeconds(9).toNanos(); // 첫 로그로부터 10초
        assertThat(log.failure("record", error)).isTrue();

        nowNanos += 1;
        assertThat(log.failure("precheck", error)).isFalse();
    }

    @Test
    void 복구는_한_번만_남기고_다음_실패는_다시_바로_남긴다() {
        log.failure("precheck", error);

        assertThat(log.recovered()).isTrue();
        assertThat(log.recovered()).isFalse(); // 이미 정상 상태

        assertThat(log.failure("precheck", error)).isTrue();
    }

    @Test
    void 실패한_적이_없으면_복구_로그도_없다() {
        assertThat(log.recovered()).isFalse();
    }
}
