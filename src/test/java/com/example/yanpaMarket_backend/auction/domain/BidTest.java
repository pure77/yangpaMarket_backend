package com.example.yanpaMarket_backend.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BidTest {

    @Test
    void create는_publicId와_생성시각을_채우고_낙찰여부는_false() {
        LocalDateTime now = LocalDateTime.now();

        Bid bid = Bid.create(10L, 99L, 65000L, now);

        assertThat(bid.getPublicId()).hasSize(26);
        assertThat(bid.getAuctionId()).isEqualTo(10L);
        assertThat(bid.getBidderUserId()).isEqualTo(99L);
        assertThat(bid.getAmount()).isEqualTo(65000L);
        assertThat(bid.getCreatedAt()).isEqualTo(now);
        assertThat(bid.isWinningBid()).isFalse();
    }
}
