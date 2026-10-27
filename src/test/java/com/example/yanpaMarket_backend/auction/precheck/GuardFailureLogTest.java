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
    void 복구는_한_번만_남기고_새_장애도_10초가_지나야_다시_남긴다() {
        assertThat(log.failure("precheck", error)).isTrue();

        assertThat(log.recovered()).isTrue();
        assertThat(log.recovered()).isFalse(); // 이미 정상 상태

        assertThat(log.failure("precheck", error)).isFalse(); // 같은 interval 안의 새 장애

        nowNanos += Duration.ofSeconds(10).toNanos();
        assertThat(log.failure("precheck", error)).isTrue();
    }

    @Test
    void 장애가_들쭉날쭉해도_한_interval에_WARN은_처음_한_번뿐이다() {
        int warns = 0;
        int infos = 0;
        assertThat(log.failure("precheck", error)).isTrue();
        warns++;
        for (int i = 0; i < 100; i++) {
            if (log.recovered()) {
                infos++;
            }
            if (log.failure("precheck", error)) {
                warns++;
            }
        }

        assertThat(warns).isEqualTo(1);
        assertThat(infos).isLessThanOrEqualTo(1);
    }

    @Test
    void WARN이_하나도_없던_장애가_끝나면_복구_로그도_없다() {
        assertThat(log.failure("precheck", error)).isTrue();
        assertThat(log.recovered()).isTrue();

        nowNanos += Duration.ofSeconds(1).toNanos();
        assertThat(log.failure("precheck", error)).isFalse(); // 통째로 억제된 장애
        assertThat(log.recovered()).isFalse();
    }

    @Test
    void 실패한_적이_없으면_복구_로그도_없다() {
        assertThat(log.recovered()).isFalse();
    }
}
