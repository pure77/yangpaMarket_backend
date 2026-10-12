package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidHistoryResponse;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidResponse;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BidServiceTest {

    @Autowired private BidService bidService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private UserRepository userRepository;

    private String sellerPublicId;
    private String bidderPublicId;
    private String auctionPublicId;

    @BeforeEach
    void setUp() {
        User seller = userRepository.save(user("seller@y.com", "판매자"));
        User bidder = userRepository.save(user("bidder@y.com", "홍길동"));
        sellerPublicId = seller.getPublicId();
        bidderPublicId = bidder.getPublicId();

        LocalDateTime now = LocalDateTime.now();
        Auction auction = Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("테스트 경매").description("설명")
                .category(AuctionCategory.ELECTRONICS)
                .itemCondition(AuctionItemCondition.LIKE_NEW)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1))
                .build();
        auctionPublicId = auctionRepository.save(auction).getPublicId();
    }

    private int phoneSeq = 0;

    private User user(String email, String nickname) {
        return User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone("010-" + (++phoneSeq) + "-0000")
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false)
                .build();
    }

    @Test
    void 입찰_성공시_현재가와_입찰수가_갱신되고_응답에_반영된다() {
        BidResponse response = bidService.placeBid(bidderPublicId, auctionPublicId, new BidRequest(20000L));

        assertThat(response.price()).isEqualTo(20000L);
        assertThat(response.currentHighestPrice()).isEqualTo(20000L);
        assertThat(response.bidCount()).isEqualTo(1);
        assertThat(response.bidId()).hasSize(26);

        Auction reloaded = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        assertThat(reloaded.getCurrentPrice()).isEqualTo(20000L);
        assertThat(reloaded.getBidCount()).isEqualTo(1);
        assertThat(reloaded.getHighestBidId()).isNotNull();
    }

    @Test
    void 최소인상폭_미만_입찰은_BID_TOO_LOW_예외() {
        assertThatThrownBy(() -> bidService.placeBid(bidderPublicId, auctionPublicId, new BidRequest(15000L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 내역_조회시_닉네임이_마스킹되고_최고가가_isHighest로_표시된다() {
        bidService.placeBid(bidderPublicId, auctionPublicId, new BidRequest(20000L));

        BidHistoryResponse history = bidService.getBids(auctionPublicId, PageRequest.of(0, 10));

        assertThat(history.totalElements()).isEqualTo(1);
        assertThat(history.content().get(0).maskedNickname()).isEqualTo("홍**");
        assertThat(history.content().get(0).isHighest()).isTrue();
    }
}
