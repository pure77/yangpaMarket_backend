package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.yanpaMarket_backend.auction.concurrency.BidLock;
import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidTooLowData;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.time.AuctionTimes;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * [무엇] 사전 거절이 실제 입찰 경로에 연결됐는지 검증한다.
 * [@Transactional 미사용] 입찰이 실제로 커밋돼야 record가 정상 경로로 실행된다. 정리는 deleteAll로 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidPrecheckIntegrationTest extends RedisTestSupport {

    @Autowired BidService bidService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @MockitoSpyBean BidLock bidLock;

    private String sellerPublicId;
    private String bidder1;
    private String bidder2;
    private String auctionPublicId;
    private Long auctionId;

    @BeforeEach
    void setUp() {
        User seller = userRepository.save(user("seller-pre@y.com", "010-7000-0001"));
        sellerPublicId = seller.getPublicId();
        bidder1 = userRepository.save(user("b1-pre@y.com", "010-7000-0002")).getPublicId();
        bidder2 = userRepository.save(user("b2-pre@y.com", "010-7000-0003")).getPublicId();
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("사전 거절 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        auctionPublicId = auction.getPublicId();
        auctionId = auction.getId();
        clearInvocations(bidLock);
    }

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 첫_입찰은_락_경로로_가고_커밋_후_Redis에_네_필드가_기록된다() {
        bidService.placeBid(bidder1, auctionPublicId, new BidRequest(20_000L));

        verify(bidLock, times(1)).executeWithLock(any(), any());
        Auction reloaded = auctionRepository.findById(auctionId).orElseThrow();
        Map<Object, Object> hash = sharedRedis().opsForHash().entries(BidRedisKeys.bid(auctionId));
        assertThat(hash).containsEntry(BidRedisKeys.PRICE, "20000")
                .containsEntry(BidRedisKeys.INC, "10000")
                .containsEntry(BidRedisKeys.SELLER, String.valueOf(reloaded.getSellerUserId()))
                .containsEntry(BidRedisKeys.END_AT, String.valueOf(AuctionTimes.toEpochMillis(reloaded.getEndAt())));
    }

    @Test
    void 낮은_입찰은_락에_들어가지_않고_기존과_같은_BID_TOO_LOW_응답() {
        bidService.placeBid(bidder1, auctionPublicId, new BidRequest(20_000L));
        clearInvocations(bidLock);

        ApiException e = catchThrowableOfType(ApiException.class,
                () -> bidService.placeBid(bidder2, auctionPublicId, new BidRequest(25_000L)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BID_TOO_LOW);
        assertThat(e.getData()).isEqualTo(new BidTooLowData(20_000L, 30_000L));
        verify(bidLock, never()).executeWithLock(any(), any());
    }

    @Test
    void 판매자_본인_입찰은_락에_들어가지_않고_SELF_BID_NOT_ALLOWED() {
        bidService.placeBid(bidder1, auctionPublicId, new BidRequest(20_000L));
        clearInvocations(bidLock);

        ApiException e = catchThrowableOfType(ApiException.class,
                () -> bidService.placeBid(sellerPublicId, auctionPublicId, new BidRequest(999_000L)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELF_BID_NOT_ALLOWED);
        verify(bidLock, never()).executeWithLock(any(), any());
    }

    @Test
    void 충분한_입찰은_사전_거절을_통과해_성공하고_기록이_올라간다() {
        bidService.placeBid(bidder1, auctionPublicId, new BidRequest(20_000L));
        bidService.placeBid(bidder2, auctionPublicId, new BidRequest(30_000L));

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getCurrentPrice()).isEqualTo(30_000L);
        assertThat(sharedRedis().opsForHash().get(BidRedisKeys.bid(auctionId), BidRedisKeys.PRICE)).isEqualTo("30000");
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
