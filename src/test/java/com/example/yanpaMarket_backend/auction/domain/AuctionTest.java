package com.example.yanpaMarket_backend.auction.domain; // 테스트 대상과 같은 패키지

import static org.assertj.core.api.Assertions.assertThat; // 가독성 좋은 검증 메서드(assertThat...)

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * [무엇] Auction 엔티티의 "도메인 규칙"을 검증하는 단위 테스트.
 *        DB/스프링 없이 순수 객체만으로 비즈니스 로직(생성/isLive/isModifiable/update/cancel)을 확인한다.
 * [연결]
 *   - 대상: Auction(경매 엔티티)의 메서드들.
 */
class AuctionTest {

    /** [테스트 헬퍼] 주어진 시작시각으로 ACTIVE 경매 하나를 만들어 반환. */
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

    /** 생성 직후: 현재가=시작가, 상태=ACTIVE, 입찰수=0 임을 검증. */
    @Test
    void 생성시_현재가는_시작가와_같고_상태는_ACTIVE_입찰수는_0() {
        Auction auction = newActiveAuction(LocalDateTime.now());

        assertThat(auction.getCurrentPrice()).isEqualTo(10000L); // 현재가 = 시작가
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(auction.getBidCount()).isEqualTo(0);
    }

    /** isLive: 공개시각이 지난 ACTIVE 경매만 true(미래 시작은 공개 유예 중이라 false). */
    @Test
    void isLive는_startAt이_지난_ACTIVE_경매에만_참() {
        LocalDateTime now = LocalDateTime.now();
        Auction past = newActiveAuction(now.minusMinutes(1));  // 이미 공개됨
        Auction future = newActiveAuction(now.plusMinutes(5)); // 아직 공개 전

        assertThat(past.isLive(now)).isTrue();
        assertThat(future.isLive(now)).isFalse(); // 5분 공개 유예 중
    }

    /** isModifiable: ACTIVE & 입찰 0건이면 수정 가능. */
    @Test
    void isModifiable은_ACTIVE이고_입찰0건일때만_참() {
        Auction auction = newActiveAuction(LocalDateTime.now());
        assertThat(auction.isModifiable()).isTrue();
    }

    /** update: 편집 필드들이 갱신되고, 시작가 변경 시 현재가도 시작가로 재설정됨. */
    @Test
    void update는_편집필드를_갱신하고_현재가를_시작가로_재설정() {
        Auction auction = newActiveAuction(LocalDateTime.now());
        LocalDateTime newEnd = LocalDateTime.now().plusDays(2);

        auction.update("수정된 제목", "수정된 설명", AuctionCategory.FASHION,
                AuctionItemCondition.USED, 20000L, 50000L, newEnd);

        assertThat(auction.getTitle()).isEqualTo("수정된 제목");
        assertThat(auction.getCategory()).isEqualTo(AuctionCategory.FASHION);
        assertThat(auction.getStartPrice()).isEqualTo(20000L);
        assertThat(auction.getCurrentPrice()).isEqualTo(20000L); // 현재가도 새 시작가로 동기화
        assertThat(auction.getBuyNowPrice()).isEqualTo(50000L);
        assertThat(auction.getEndAt()).isEqualTo(newEnd);
    }

    /** cancel: 상태가 CANCELLED로 바뀌고 사유/시각이 기록되며, 이후 수정 불가. */
    @Test
    void cancel은_상태를_CANCELLED로_바꾸고_사유와_시각을_기록() {
        Auction auction = newActiveAuction(LocalDateTime.now());

        auction.cancel("판매자 취소");

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(auction.getCancelReason()).isEqualTo("판매자 취소");
        assertThat(auction.getCancelledAt()).isNotNull();
        assertThat(auction.isModifiable()).isFalse(); // 취소 후엔 수정 불가
    }
}
