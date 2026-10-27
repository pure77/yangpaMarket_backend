package com.example.yanpaMarket_backend.auction.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BidRedisKeysTest {

    @Test
    void 경매_ID를_해시_태그로_감싼다() {
        // [왜 고정하나] {42}가 Cluster 해시 슬롯을 정한다. 2단계 락 키 auction:{42}:lock과 같은 슬롯이어야 한다.
        assertThat(BidRedisKeys.bid(42L)).isEqualTo("auction:{42}:bid");
    }
}
