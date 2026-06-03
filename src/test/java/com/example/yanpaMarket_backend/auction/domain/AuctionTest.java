package com.example.yanpaMarket_backend.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTest {

    private Auction newActiveAuction(LocalDateTime startAt) {
        return Auction.builder()
                .publicId("01HZX0000000000000000TEST1")
                .sellerUserId(1L)
                .title("테스트 경매")
                .description("설명")
                .category(AuctionCategory.ELECTRONICS)
                .itemCondition(AuctionItemCondition.LIKE_NEW)
                .startPrice(10000L)
                .buyNowPrice(null)
                .startAt(startAt)
                .endAt(startAt.plusDays(1))
                .build();
    }

    @Test
    void 생성시_현재가는_시작가와_같고_상태는_ACTIVE_입찰수는_0() {
        Auction auction = newActiveAuction(LocalDateTime.now());

        assertThat(auction.getCurrentPrice()).isEqualTo(10000L);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(auction.getBidCount()).isEqualTo(0);
    }

    @Test
    void isLive는_startAt이_지난_ACTIVE_경매에만_참() {
        LocalDateTime now = LocalDateTime.now();
        Auction past = newActiveAuction(now.minusMinutes(1));
        Auction future = newActiveAuction(now.plusMinutes(5));

        assertThat(past.isLive(now)).isTrue();
        assertThat(future.isLive(now)).isFalse(); // 5분 공개 유예 중
    }

    @Test
    void isModifiable은_ACTIVE이고_입찰0건일때만_참() {
        Auction auction = newActiveAuction(LocalDateTime.now());
        assertThat(auction.isModifiable()).isTrue();
    }

    @Test
    void update는_편집필드를_갱신하고_현재가를_시작가로_재설정() {
        Auction auction = newActiveAuction(LocalDateTime.now());
        LocalDateTime newEnd = LocalDateTime.now().plusDays(2);

        auction.update("수정된 제목", "수정된 설명", AuctionCategory.FASHION,
                AuctionItemCondition.USED, 20000L, 50000L, newEnd);

        assertThat(auction.getTitle()).isEqualTo("수정된 제목");
        assertThat(auction.getCategory()).isEqualTo(AuctionCategory.FASHION);
        assertThat(auction.getStartPrice()).isEqualTo(20000L);
        assertThat(auction.getCurrentPrice()).isEqualTo(20000L);
        assertThat(auction.getBuyNowPrice()).isEqualTo(50000L);
        assertThat(auction.getEndAt()).isEqualTo(newEnd);
    }

    @Test
    void cancel은_상태를_CANCELLED로_바꾸고_사유와_시각을_기록() {
        Auction auction = newActiveAuction(LocalDateTime.now());

        auction.cancel("판매자 취소");

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(auction.getCancelReason()).isEqualTo("판매자 취소");
        assertThat(auction.getCancelledAt()).isNotNull();
        assertThat(auction.isModifiable()).isFalse();
    }
}
