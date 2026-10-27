package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.precheck.BidPriceGuard;
import com.example.yanpaMarket_backend.auction.precheck.RedisBidPriceGuard;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * [무엇] 사전 거절은 켜져 있는데 Redis에 연결할 수 없을 때도 입찰이 지금처럼 동작하는지 본다(fail-open).
 * [Docker 불필요] 아무도 듣지 않는 포트(1)를 가리킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.bid.precheck.enabled=true",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1"
})
class BidPrecheckRedisDownTest {

    @Autowired BidService bidService;
    @Autowired BidPriceGuard bidPriceGuard;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @MockitoSpyBean SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void Redis가_없어도_입찰은_성공하고_브로드캐스트되며_낮은_입찰은_락에서_거절된다() {
        assertThat(bidPriceGuard).isInstanceOf(RedisBidPriceGuard.class); // 정말 켜진 상태에서 시험하는지

        User seller = userRepository.save(user("seller-down@y.com", "010-7100-0001"));
        String bidder1 = userRepository.save(user("b1-down@y.com", "010-7100-0002")).getPublicId();
        String bidder2 = userRepository.save(user("b2-down@y.com", "010-7100-0003")).getPublicId();
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("Redis 장애 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());

        bidService.placeBid(bidder1, auction.getPublicId(), new BidRequest(20_000L));

        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getCurrentPrice()).isEqualTo(20_000L);
        verify(messagingTemplate).convertAndSend(eq("/topic/auction/" + auction.getPublicId()), any(Object.class));

        ApiException e = catchThrowableOfType(ApiException.class,
                () -> bidService.placeBid(bidder2, auction.getPublicId(), new BidRequest(25_000L)));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BID_TOO_LOW); // 사전 거절 없이 락 안 도메인이 거절
        assertThat(((RedisBidPriceGuard) bidPriceGuard).stats().precheckFailures()).isEqualTo(2);
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
